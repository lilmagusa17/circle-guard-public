#!/bin/bash
# create-jenkins-jobs.sh
# Crea los 18 pipeline jobs en Jenkins via CLI
# Uso: bash jenkins/scripts/create-jenkins-jobs.sh

JENKINS_URL=${JENKINS_URL:-"http://localhost:8090"}
JENKINS_USER=${JENKINS_USER:-"admin"}
JENKINS_TOKEN=${JENKINS_TOKEN:-""}
REPO_URL=${REPO_URL:-"https://github.com/TU_ORG/circle-guard-public"}

if [ -z "$JENKINS_TOKEN" ]; then
  echo "ERROR: Debes definir JENKINS_TOKEN"
  exit 1
fi

AUTH="${JENKINS_USER}:${JENKINS_TOKEN}"

# Obtener CSRF crumb (requerido por Jenkins moderno)
echo "Obteniendo CSRF crumb..."
CRUMB_JSON=$(curl -s -u "$AUTH" "${JENKINS_URL}/crumbIssuer/api/json")
CRUMB_FIELD=$(echo "$CRUMB_JSON" | grep -o '"crumbRequestField":"[^"]*"' | cut -d'"' -f4)
CRUMB_VALUE=$(echo "$CRUMB_JSON" | grep -o '"crumb":"[^"]*"' | cut -d'"' -f4)

if [ -z "$CRUMB_VALUE" ]; then
  echo "WARN: No se pudo obtener crumb (Jenkins puede no tener CSRF activo, continuando...)"
  CRUMB_HEADER=""
else
  echo "Crumb obtenido OK"
  CRUMB_HEADER="${CRUMB_FIELD}:${CRUMB_VALUE}"
fi

SERVICES=(file-service form-service gateway-service identity-service notification-service promotion-service)
ENVS=(dev stage master)

create_job() {
  local job_name=$1
  local script_path=$2

  printf "Creando job: %-40s" "$job_name"

  local curl_args=(-s -o /dev/null -w "%{http_code}" -X POST
    "${JENKINS_URL}/createItem?name=${job_name}"
    -u "$AUTH"
    -H "Content-Type: application/xml")

  [ -n "$CRUMB_HEADER" ] && curl_args+=(-H "$CRUMB_HEADER")

  HTTP_CODE=$(cat <<EOF | curl "${curl_args[@]}" --data-binary @-
<flow-definition plugin="workflow-job">
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition" plugin="workflow-cps">
    <scm class="hudson.plugins.git.GitSCM" plugin="git">
      <configVersion>2</configVersion>
      <userRemoteConfigs>
        <hudson.plugins.git.UserRemoteConfig>
          <url>${REPO_URL}</url>
        </hudson.plugins.git.UserRemoteConfig>
      </userRemoteConfigs>
      <branches>
        <hudson.plugins.git.BranchSpec>
          <name>*/master</name>
        </hudson.plugins.git.BranchSpec>
      </branches>
    </scm>
    <scriptPath>${script_path}</scriptPath>
    <lightweight>true</lightweight>
  </definition>
  <triggers/>
</flow-definition>
EOF
)

  case "$HTTP_CODE" in
    200|201) echo "✓ (${HTTP_CODE})" ;;
    400)     echo "⚠ Ya existe (${HTTP_CODE})" ;;
    401)     echo "✗ Unauthorized (${HTTP_CODE}) — verifica usuario/token" ;;
    403)     echo "✗ Forbidden (${HTTP_CODE}) — crumb inválido" ;;
    *)       echo "✗ Error (${HTTP_CODE})" ;;
  esac
}

echo ""
echo "=== Creando 18 jobs en Jenkins ==="
echo "URL: $JENKINS_URL"
echo ""

for service in "${SERVICES[@]}"; do
  for env in "${ENVS[@]}"; do
    job_name="${service}-${env}"
    script_path="services/circleguard-${service}/Jenkinsfile.${env}"
    create_job "$job_name" "$script_path"
  done
done

echo ""
echo "=== Verifica en: $JENKINS_URL ==="
