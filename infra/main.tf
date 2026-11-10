# Infraestructura del backend de Tarjetazo en Google Cloud, con OpenTofu (compatible con Terraform).
#   tofu init && tofu plan -var-file=tarjetazo.tfvars
# La base de Firestore y Firebase ya existen: se crearon desde la consola de Firebase.

terraform {
  required_providers {
    google = { source = "hashicorp/google", version = "~> 6.0" }
  }
}

variable "proyecto" { type = string }
variable "imagen" { type = string } # p. ej. southamerica-east1-docker.pkg.dev/<proyecto>/tarjetazo/backend:1
variable "cuenta_facturacion" { type = string }
variable "region" {
  type    = string
  default = "southamerica-east1" # San Pablo: la región más cercana a los usuarios, en Argentina
}

provider "google" {
  project = var.proyecto
  region  = var.region
}

data "google_project" "este" {}

# ---------- Identidades: cada pieza con lo mínimo que necesita ----------

resource "google_service_account" "backend" { account_id = "tarjetazo-backend" }
resource "google_service_account" "pubsub_push" { account_id = "tarjetazo-pubsub-push" }
resource "google_service_account" "scheduler" { account_id = "tarjetazo-scheduler" }

resource "google_project_iam_member" "backend" {
  for_each = toset(["roles/datastore.user", "roles/pubsub.publisher", "roles/firebasecloudmessaging.admin"])
  project  = var.proyecto
  role     = each.value
  member   = "serviceAccount:${google_service_account.backend.email}"
}

# ---------- API: recibe consumos y responde 202 ----------

resource "google_cloud_run_v2_service" "api" {
  name     = "tarjetazo-api"
  location = var.region
  template {
    service_account = google_service_account.backend.email
    # Escalado dinámico: 0 instancias sin tráfico (no se paga nada), hasta 10 en un pico.
    # El tope es también un techo de gasto. Cloud Run reparte los pedidos entre las instancias
    # (balanceo incluido) y las distribuye entre zonas de la región (redundancia).
    scaling {
      min_instance_count = 0
      max_instance_count = 10
    }
    # Pedidos simultáneos por instancia antes de crear otra: publicar en la cola es liviano.
    max_instance_request_concurrency = 80
    containers {
      image = var.imagen
      env {
        name  = "GOOGLE_CLOUD_PROJECT"
        value = var.proyecto
      }
      resources {
        limits   = { cpu = "1", memory = "512Mi" }
        cpu_idle = true # CPU solo mientras atiende: se factura por pedido, no por hora
      }
    }
  }
}
# Sin invocador público: solo llaman los sistemas autorizados (importador, banco) con su cuenta de servicio.

# ---------- Cola: Pub/Sub entre la API y el worker ----------

resource "google_pubsub_topic" "consumos" { name = "consumos" }
resource "google_pubsub_topic" "consumos_errores" { name = "consumos-errores" }

resource "google_pubsub_subscription" "consumos_errores" {
  name                       = "consumos-errores"
  topic                      = google_pubsub_topic.consumos_errores.id
  message_retention_duration = "604800s" # una semana para revisar a mano lo que falló
}

resource "google_pubsub_subscription" "consumos_worker" {
  name                 = "consumos-worker"
  topic                = google_pubsub_topic.consumos.id
  ack_deadline_seconds = 30
  push_config {
    push_endpoint = "${google_cloud_run_v2_service.worker.uri}/pubsub/consumos"
    oidc_token { service_account_email = google_service_account.pubsub_push.email }
  }
  # Si el worker falla, se reintenta cada vez más espaciado; a los 5 intentos va a la cola de errores
  # y deja de bloquear al resto.
  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "300s"
  }
  dead_letter_policy {
    dead_letter_topic     = google_pubsub_topic.consumos_errores.id
    max_delivery_attempts = 5
  }
}

# El agente de Pub/Sub necesita mover los mensajes fallidos a la cola de errores.
locals {
  agente_pubsub = "serviceAccount:service-${data.google_project.este.number}@gcp-sa-pubsub.iam.gserviceaccount.com"
}
resource "google_pubsub_topic_iam_member" "errores_publica" {
  topic  = google_pubsub_topic.consumos_errores.name
  role   = "roles/pubsub.publisher"
  member = local.agente_pubsub
}
resource "google_pubsub_subscription_iam_member" "errores_suscribe" {
  subscription = google_pubsub_subscription.consumos_worker.name
  role         = "roles/pubsub.subscriber"
  member       = local.agente_pubsub
}

# ---------- Worker: procesa lo que entrega la cola ----------

resource "google_cloud_run_v2_service" "worker" {
  name     = "tarjetazo-worker"
  location = var.region
  template {
    service_account = google_service_account.backend.email
    timeout         = "30s"
    # Escala con los mensajes que Pub/Sub le empuja. El tope es más bajo que el de la API a propósito:
    # en un pico la cola crece y se vacía a este ritmo, en lugar de pasarle la avalancha a Firestore.
    scaling {
      min_instance_count = 0
      max_instance_count = 5
    }
    max_instance_request_concurrency = 20
    containers {
      image = var.imagen
      env {
        name  = "GOOGLE_CLOUD_PROJECT"
        value = var.proyecto
      }
      resources {
        limits   = { cpu = "1", memory = "512Mi" }
        cpu_idle = true
      }
    }
  }
}

resource "google_cloud_run_v2_service_iam_member" "push_invoca_worker" {
  name     = google_cloud_run_v2_service.worker.name
  location = var.region
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.pubsub_push.email}"
}

# ---------- Tareas programadas: cotización y recordatorio ----------

resource "google_cloud_run_v2_job" "tarea" {
  for_each = toset(["cotizacion", "recordatorio"])
  name     = "tarjetazo-${each.key}"
  location = var.region
  template {
    template {
      service_account = google_service_account.backend.email
      max_retries     = 2
      containers {
        image   = var.imagen
        command = ["python"]
        args    = ["tareas.py", each.key]
        env {
          name  = "GOOGLE_CLOUD_PROJECT"
          value = var.proyecto
        }
      }
    }
  }
}

resource "google_cloud_run_v2_job_iam_member" "scheduler_ejecuta" {
  for_each = google_cloud_run_v2_job.tarea
  name     = each.value.name
  location = var.region
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.scheduler.email}"
}

resource "google_cloud_scheduler_job" "tarea" {
  for_each = {
    cotizacion   = "*/30 * * * *"  # cada 30 minutos
    recordatorio = "0 10 20-24 * *" # 10 h, del 20 al 24: antes del cierre más común
  }
  name      = "tarjetazo-${each.key}"
  region    = var.region
  schedule  = each.value
  time_zone = "America/Argentina/Buenos_Aires"
  http_target {
    http_method = "POST"
    uri         = "https://run.googleapis.com/v2/projects/${var.proyecto}/locations/${var.region}/jobs/tarjetazo-${each.key}:run"
    oauth_token { service_account_email = google_service_account.scheduler.email }
  }
  depends_on = [google_cloud_run_v2_job.tarea]
}

# ---------- Monetización: avisos de Google Play sobre las suscripciones (TP 4) ----------
# Google Play publica en este tema cada cambio de una suscripción (compra, renovación, cancelación,
# pago rechazado). El nombre completo del tema se carga en Play Console > Monetización > Configuración.

resource "google_project_service" "android_publisher" {
  service            = "androidpublisher.googleapis.com" # la API que el worker consulta por cada aviso
  disable_on_destroy = false
}

resource "google_pubsub_topic" "play" { name = "play-rtdn" }
resource "google_pubsub_topic" "play_errores" { name = "play-rtdn-errores" }

# La cuenta con que Google Play publica los avisos. Es la misma para todas las apps.
resource "google_pubsub_topic_iam_member" "play_publica" {
  topic  = google_pubsub_topic.play.name
  role   = "roles/pubsub.publisher"
  member = "serviceAccount:google-play-developer-notifications@system.gserviceaccount.com"
}

resource "google_pubsub_subscription" "play_errores" {
  name                       = "play-rtdn-errores"
  topic                      = google_pubsub_topic.play_errores.id
  message_retention_duration = "604800s"
}

# Mismo patrón que los consumos: push autenticado al worker, reintentos y cola de errores.
# Un aviso perdido es un usuario que pagó y no tiene Plus, así que tampoco se descarta nada.
resource "google_pubsub_subscription" "play_worker" {
  name                 = "play-rtdn-worker"
  topic                = google_pubsub_topic.play.id
  ack_deadline_seconds = 30
  push_config {
    push_endpoint = "${google_cloud_run_v2_service.worker.uri}/pubsub/play"
    oidc_token { service_account_email = google_service_account.pubsub_push.email }
  }
  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "300s"
  }
  dead_letter_policy {
    dead_letter_topic     = google_pubsub_topic.play_errores.id
    max_delivery_attempts = 5
  }
}

resource "google_pubsub_topic_iam_member" "play_errores_publica" {
  topic  = google_pubsub_topic.play_errores.name
  role   = "roles/pubsub.publisher"
  member = local.agente_pubsub
}
resource "google_pubsub_subscription_iam_member" "play_errores_suscribe" {
  subscription = google_pubsub_subscription.play_worker.name
  role         = "roles/pubsub.subscriber"
  member       = local.agente_pubsub
}

# La cuenta de servicio del backend no se puede autorizar en Google Play desde acá: se invita a mano
# en Play Console > Usuarios y permisos, con permiso para ver datos financieros y administrar pedidos.
output "cuenta_backend" { value = google_service_account.backend.email }
output "tema_play" { value = google_pubsub_topic.play.id }

# ---------- Costos: aviso de presupuesto ----------
# Avisa por correo; no corta el servicio. El corte real lo ponen los max_instance_count de arriba.

resource "google_billing_budget" "tope" {
  billing_account = var.cuenta_facturacion
  display_name    = "Tarjetazo"
  budget_filter { projects = ["projects/${data.google_project.este.number}"] }
  amount {
    specified_amount {
      currency_code = "USD"
      units         = "10"
    }
  }
  threshold_rules { threshold_percent = 0.5 }
  threshold_rules { threshold_percent = 0.9 }
  threshold_rules { threshold_percent = 1.0 }
}

output "api" { value = google_cloud_run_v2_service.api.uri }
