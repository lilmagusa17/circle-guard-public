# Pipeline Notifications

CircleGuard pipelines send Slack notifications on pipeline failure and production success.

## Setup

### Slack Webhook

1. Create an Incoming Webhook in your Slack workspace (Settings → Apps → Incoming Webhooks).
2. Add the webhook URL as a Jenkins credential:
   - **ID:** `slack-webhook`
   - **Type:** Secret text
   - **Value:** `https://hooks.slack.com/services/...`

### Jenkins Credential

```
Manage Jenkins → Credentials → System → Global → Add Credentials
Kind: Secret text
ID: slack-webhook
```

## Notification Events

| Event | Pipeline | Message |
|-------|----------|---------|
| Pipeline failure | DEV, Stage, Master | ❌ Job name + build number + URL |
| Production success | Master | ✅ Version deployed to production |

## Message Format

Failure:
```
❌ *CircleGuard DEV* pipeline failed
Job: circleguard-dev #42
URL: http://jenkins:8080/job/circleguard-dev/42/
```

Success (master only):
```
✅ *CircleGuard MASTER* deployed v1.2.3 to production
Job: circleguard-master #10
```

## Implementation

Notifications use `curl` in the `post { failure/success {} }` block of each Jenkinsfile. The `|| true` ensures the curl failure never fails the build itself.

```groovy
post {
    failure {
        withCredentials([string(credentialsId: 'slack-webhook', variable: 'SLACK_URL')]) {
            sh """
                curl -s -X POST -H 'Content-type: application/json' \
                    --data '{"text":"❌ *CircleGuard DEV* pipeline failed\\nJob: ${env.JOB_NAME} #${env.BUILD_NUMBER}"}' \
                    "\${SLACK_URL}" || true
            """
        }
    }
}
```

## Graduating to Alertmanager

Phase 7 will configure Prometheus Alertmanager to send infrastructure alerts (pod restarts, high latency, error rate) to the same Slack channel. See [`docs/operations/alerts.md`](alerts.md).
