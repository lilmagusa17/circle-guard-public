/**
 * Shared Library: circleguardPipeline
 * Reutilizable por los 6 microservicios en los 3 ambientes.
 *
 * DEV:    Checkout → Build → Unit Tests → Docker Build (local)
 * STAGE:  + Integration Tests → E2E Tests → Deploy K8s (circleguard-stage)
 * MASTER: + Approval → Performance Tests → Deploy K8s (circleguard-master) → Release Notes
 */
def call(Map config) {
    def service      = config.service
    def env          = config.environment ?: 'dev'
    def namespace    = "circleguard-${env}"
    def imageTag     = env == 'master' ? 'latest' : env
    def image        = "circleguard/${service}:${imageTag}"
    def gradleModule = ":services:circleguard-${service}"
    def deployToK8s  = (env == 'stage' || env == 'master')

    pipeline {
        agent any

        tools {
            jdk 'JDK-21'
        }

        options {
            disableConcurrentBuilds()
            buildDiscarder(logRotator(numToKeepStr: '10'))
        }

        environment {
            KUBECONFIG = credentials('kubeconfig')
        }

        stages {

            stage('Checkout') {
                steps {
                    checkout scm
                }
            }

            stage('Build') {
                steps {
                    sh "./gradlew ${gradleModule}:bootJar -x test --no-daemon"
                }
            }

            stage('Unit Tests') {
                steps {
                    sh "./gradlew ${gradleModule}:test --no-daemon"
                }
                post {
                    always {
                        junit "services/circleguard-${service}/build/test-results/test/*.xml"
                    }
                }
            }

            stage('Docker Build') {
                steps {
                    sh """
                        docker build --pull=false \\
                          -f services/circleguard-${service}/Dockerfile \\
                          -t ${image} .
                    """
                }
            }

            // Solo STAGE y MASTER cargan la imagen en Minikube y despliegan
            // Jenkins comparte el Docker socket del host, así que usamos docker exec
            // para cargar la imagen directamente en el contenedor minikube del host.
            stage('Load Image to Minikube') {
                when { expression { deployToK8s } }
                steps {
                    sh "docker save ${image} | docker exec -i minikube docker load"
                }
            }

            stage('Integration Tests') {
                when { expression { config.runIntegration == true } }
                steps {
                    sh "./gradlew :tests:integration:test --no-daemon"
                }
                post {
                    always {
                        junit 'tests/integration/build/test-results/test/*.xml'
                    }
                }
            }

            stage('Deploy to Kubernetes') {
                when { expression { deployToK8s } }
                steps {
                    sh "kubectl apply -f k8s/${env}/${service}.yaml --namespace=${namespace} --insecure-skip-tls-verify=true"
                    sh "kubectl rollout status deployment/${service} --namespace=${namespace} --timeout=120s --insecure-skip-tls-verify=true"
                }
            }

            stage('E2E Tests') {
                when { expression { config.runE2E == true } }
                steps {
                    sh "./gradlew :tests:e2e:test --no-daemon -Denv=${env}"
                }
                post {
                    always {
                        junit 'tests/e2e/build/test-results/test/*.xml'
                    }
                }
            }

            stage('Approval') {
                when { expression { config.requireApproval == true } }
                steps {
                    timeout(time: 30, unit: 'MINUTES') {
                        input message: "¿Aprobar despliegue a MASTER de ${service}?", ok: 'Desplegar'
                    }
                }
            }

            stage('Performance Tests') {
                when { expression { config.runPerformance == true } }
                steps {
                    sh """
                        pip install locust --quiet
                        locust -f tests/performance/locustfile.py \\
                               --headless -u 50 -r 5 -t 60s \\
                               --host=http://${service}.${namespace}.svc.cluster.local:8080 \\
                               --csv=build/locust/${service}
                    """
                }
                post {
                    always {
                        archiveArtifacts artifacts: 'build/locust/**/*.csv', allowEmptyArchive: true
                    }
                }
            }

            stage('Release Notes') {
                when { expression { config.generateReleaseNotes == true } }
                steps {
                    sh 'bash jenkins/scripts/generate-release-notes.sh'
                    archiveArtifacts artifacts: 'CHANGELOG.md'
                }
            }
        }

        post {
            success {
                echo "✅ Pipeline ${service} [${env}] completado exitosamente."
            }
            failure {
                echo "❌ Pipeline ${service} [${env}] falló."
            }
        }
    }
}
