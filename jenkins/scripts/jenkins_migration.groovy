import jenkins.model.*
import hudson.model.*
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
import hudson.plugins.git.BranchSpec

def jenkins = Jenkins.get()

def servicesStr = System.getenv("SERVICES") ?: 'file-service,form-service,gateway-service,identity-service,notification-service,promotion-service'
def services = servicesStr.split(',').collect { it.trim() }

def repoUrl = System.getenv("REPO_URL") ?: "https://github.com/jcmunozf/circle-guard-public.git"
def branch = System.getenv("BRANCH") ?: "*/main"

println "--- DELETING OLD JOBS ---"
services.each { service ->
    def stageJob = jenkins.getItemByFullName("${service}-stage")
    if (stageJob != null) {
        println "Deleting job: ${stageJob.fullName}"
        stageJob.delete()
    }
    
    def masterJob = jenkins.getItemByFullName("${service}-master")
    if (masterJob != null) {
        println "Deleting job: ${masterJob.fullName}"
        masterJob.delete()
    }
}

println "\n--- CREATING NEW JOBS ---"

// 1. Create the new system-wide stage job
def stageJobName = "system-stage"
if (jenkins.getItemByFullName(stageJobName) == null) {
    def stageJob = jenkins.createProject(WorkflowJob.class, stageJobName)
    def gitSCM = new GitSCM(
        [new UserRemoteConfig(repoUrl, null, null, null)],
        [new BranchSpec(branch)],
        false, Collections.emptyList(), null, null, Collections.emptyList()
    )
    def flowDefinition = new CpsScmFlowDefinition(gitSCM, "jenkins/pipelines/Jenkinsfile.stage")
    flowDefinition.setLightweight(true)
    stageJob.setDefinition(flowDefinition)
    stageJob.save()
    println "Created job: ${stageJobName}"
} else {
    println "Job ${stageJobName} already exists"
}

// 2. Create the new system-wide master job
def masterJobName = "system-master"
if (jenkins.getItemByFullName(masterJobName) == null) {
    def masterJob = jenkins.createProject(WorkflowJob.class, masterJobName)
    def gitSCM = new GitSCM(
        [new UserRemoteConfig(repoUrl, null, null, null)],
        [new BranchSpec(branch)],
        false, Collections.emptyList(), null, null, Collections.emptyList()
    )
    def flowDefinition = new CpsScmFlowDefinition(gitSCM, "jenkins/pipelines/Jenkinsfile.master")
    flowDefinition.setLightweight(true)
    masterJob.setDefinition(flowDefinition)
    masterJob.save()
    println "Created job: ${masterJobName}"
} else {
    println "Job ${masterJobName} already exists"
}

println "\n--- MIGRATION COMPLETE ---"
