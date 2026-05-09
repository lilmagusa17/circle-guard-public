/**
 * Shared Library: circleguardPipeline
 * Reutilizable por los 6 microservicios en los 3 ambientes.
 *
 * Uso:
 *   circleguardPipeline(
 *     service: 'form-service',
 *     environment: 'dev',          // dev | stage | master
 *     runIntegration: false,
 *     runE2E: false,
 *     runPerformance: false,
 *     requireApproval: false
 *   )
 */
def call(Map config) {
    def service     = config.service
    def env         = config.environment ?: 'dev'
    def namespace   = "circleguard-${env}"
    def imageTag    = env == 'master' ? 'latest' : env
    def image       = "circleguard/${service}:${imageTag}"
    def gradleModule = ":services:circleguard-${service}"

    pipeline {
        agent any

        tools {
            jdk 'JDK-21'
        }

        options {
            timestamps()
            disableConcurrentBuilds()
            buildDiscarder(logRotator(numToKeepStr: '10'))
        }

        environment {
            DOCKER_REGISTRY = credentials('docker-registry-credentials')
            KUBECONFIG      = credentials('kubeconfig')
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

            stage('Docker Build & Push') {
                steps {
                    sh """
                        docker build \
                          -f services/circleguard-${service}/Dockerfile \
                          -t ${image} .
                        echo \$DOCKER_REGISTRY_PSW | docker login -u \$DOCKER_REGISTRY_USR --password-stdin
                        docker push ${image}
                    """
                }
            }

            stage('Deploy') {
                steps {
                    sh "kubectl apply -f k8s/${env}/${service}.yaml --namespace=${namespace}"
                    sh "kubectl rollout status deployment/${service} --namespace=${namespace} --timeout=120s"
                }
            }

            stage('Integration Tests') {
                when { expression { config.runIntegration == true } }
                steps {
                    sh "./gradlew ${gradleModule}:integrationTest --no-daemon"
                }
                post {
                    always {
                        junit "services/circleguard-${service}/build/test-results/integrationTest/*.xml"
                    }
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
                        input message: "¿Aprobar despliegue a MASTER?", ok: 'Desplegar'
                    }
                }
            }

            stage('Performance Tests') {
                when { expression { config.runPerformance == true } }
                steps {
                    sh """
                        pip install locust --quiet
                        locust -f tests/performance/locustfile.py \
                               --headless -u 50 -r 5 -t 60s \
                               --host=http://${service}.${namespace}.svc.cluster.local:8080 \
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
                echo "Pipeline ${service} [${env}] completado exitosamente."
            }
            failure {
                echo "Pipeline ${service} [${env}] falló."
            }
        }
    }
}
