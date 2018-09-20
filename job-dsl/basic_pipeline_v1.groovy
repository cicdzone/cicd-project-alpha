import jenkins.model.*

createBuildJob()
createDEVJob()
createTestJob("DEV01","dev01")
createQAJob("01")
createTestJob("QA01","qa01")
createPromoteJob()
createUATJob()
createTestJob("UAT","uat01")
createPRODJob()
createTestJob("PRODUCTION","prd01")
createRollbackJob()
createTestJob("ROLLBACK","prd01")
createPipeline()

/**
 * Function to create the Build Job 
 *
 */
def createBuildJob() {
  folder("${TEAM}/${APP_NAME}") {
        description("Folder containing all jobs for ${APP_NAME}")
  }
  freeStyleJob("${TEAM}/${APP_NAME}/${APP_NAME}-Build") {
        wrappers {
           preBuildCleanup()
        }
        description("Build Application<br/><a href=\"/view/${APP_NAME}/\">${APP_NAME} Pipeline</a>")
        authorization {
            permissions("Admin", [
                'hudson.model.Item.Build',
                'hudson.model.Item.Read',
                'hudson.model.Item.Cancel'
            ])
        }
        environmentVariables {
            env('IMAGE_NAME', "${APP_NAME}")
        }
        scm {
            git {
                remote {
                    url("\${CODE_REPO}/${APP_NAME}.git")
                    credentials('bitbucket-repo-admin')
                }
                branch('master')
           }
        }
        triggers {
            scm('H/3 * * * *')
        }
        steps {
            shell('''
echo build something
#We need to create the variable IMAGE_VERSION. We pass this variable to the other jobs in the pipeline.
IMAGE_VERSION="1.0.${BUILD_NUMBER}"
echo "IMAGE_VERSION=${IMAGE_VERSION}" >> cicd_vars.txt
            ''')
            environmentVariables {
                propertiesFile('$WORKSPACE/cicd_vars.txt')
            }
        }
        publishers {
               downstreamParameterized {
                  trigger("${TEAM}/${APP_NAME}/${APP_NAME}-Deploy-DEV") {
                        condition('UNSTABLE_OR_BETTER')
                        parameters {
                            predefinedProp('IMAGE_VERSION', '${IMAGE_VERSION}')
                        }
                    }
                }
        }
        properties{
            configure {
                it / 'properties'  <<  'se.diabol.jenkins.pipeline.PipelineProperty' {
                    taskName 'Build Image'
                    stageName 'Build'
                    descriptionTemplate """<b>Image:</b> \${ENV,var="IMAGE_NAME"}<br/>
<b>Version:</b> \${ENV,var="IMAGE_VERSION"}<br/>
<b>Branch:</b> <a href="${CODE_REPO}/${APP_NAME}/src/\${ENV,var="GIT_COMMIT"}/?at=\${ENV:7,var="GIT_BRANCH"}" target="_blank">\${ENV,var="GIT_BRANCH"}</a><br/>
"""
                }
            }
        }
    }
}

/**
 * Function to create the Job that Deploys to DEV 
 *
 */
def createDEVJob() {
    freeStyleJob("${TEAM}/${APP_NAME}/${APP_NAME}-Deploy-DEV") {
        description("1. Update Task Definition.")
        authorization {
            permissions("Admin", [
                'hudson.model.Item.Build',
                'hudson.model.Item.Read',
                'hudson.model.Item.Cancel'
            ])

        }
        environmentVariables {
            env('IMAGE_NAME', "${APP_NAME}")
            env('APP_ENV', 'dev01')
        }
        scm {

        }
        parameters {
            stringParam('IMAGE_VERSION', '', 'The version of the IMAGE to deploy')
        }
        steps {
            shell("echo deploy")
        }
        publishers {

            // automatically kicked off
            downstreamParameterized {
               trigger("${TEAM}/${APP_NAME}/${APP_NAME}-Test-DEV01") {
                   condition('UNSTABLE_OR_BETTER')
                   parameters {
                      predefinedProp('IMAGE_VERSION', '${IMAGE_VERSION}')
                   }
               }
            }
            // manually kicked off
            buildPipelineTrigger("${TEAM}/${APP_NAME}/${APP_NAME}-Deploy-QA01") {
               parameters {
                   predefinedProp('IMAGE_VERSION', '${IMAGE_VERSION}')
               }
            }
        }

        properties{
            configure {
                it / 'properties'  <<  'se.diabol.jenkins.pipeline.PipelineProperty' {
                    taskName 'Deploy to ${ENV,var="APP_ENV"}'
                    stageName "DEV01"
                    descriptionTemplate """<b>Version:</b> \${ENV,var="IMAGE_VERSION"} """
                }
            }
        }
    }
}

/**
 * Function that creates the Job to deploy to QA
 *
 */
def createQAJob(String LINE) {
    def nextJob="${APP_NAME}-Promote"
    freeStyleJob("${TEAM}/${APP_NAME}/${APP_NAME}-Deploy-QA${LINE}") {
        description("Deploy the App.")
        authorization {
            permissions("QA", [
                'hudson.model.Item.Build',
                'hudson.model.Item.Read',
                'hudson.model.Item.Cancel'
            ])
        }
        environmentVariables {
            env('IMAGE_NAME', "${APP_NAME}")
            env('APP_ENV', "qa${LINE}")
        }
        scm {

        }
        parameters {
            stringParam('IMAGE_VERSION', '', 'The version of the IMAGE to deploy')
        }
        steps {
            shell("echo deploy")
        }
        publishers {


               //automated tests
               downstreamParameterized {
                  trigger("${TEAM}/${APP_NAME}/${APP_NAME}-Test-QA${LINE}") {
                        condition('UNSTABLE_OR_BETTER')
                        parameters {
                            predefinedProp('IMAGE_VERSION', '${IMAGE_VERSION}')
                        }
                    }
                }
                buildPipelineTrigger("${TEAM}/${APP_NAME}/${nextJob}") {
                    parameters {
                        predefinedProp('IMAGE_VERSION', '${IMAGE_VERSION}')
                    }
                }
        }
        properties{
            configure {
                it / 'properties'  <<  'se.diabol.jenkins.pipeline.PipelineProperty' {
                    taskName 'Deploy to ${ENV,var="APP_ENV"}'
                    stageName "QA$LINE"
                    descriptionTemplate """<b>Version:</b> \${ENV,var="IMAGE_VERSION"} """
                }
            }
        }
    }
}

/**
 * Function that creates the Test Jobs
 */
def createTestJob( String STAGE_NAME, String ENV ) {

  freeStyleJob("${TEAM}/${APP_NAME}/${APP_NAME}-Test-${STAGE_NAME}") {
    description("Run automated Tests against ${ENV}.")

        authorization {
            permissions("QA", [
                'hudson.model.Item.Build',
                'hudson.model.Item.Read',
                'hudson.model.Item.Cancel'
            ])
        }

        environmentVariables {
            env('ENV', "${ENV}")
            env('IMAGE_NAME',"${APP_NAME}")
        }

        steps {
              shell("echo test")
        }
        publishers {
        }
        properties{
            configure {
                it / 'properties'  <<  'se.diabol.jenkins.pipeline.PipelineProperty' {
                    taskName "Test ${ENV}"
                    stageName "${STAGE_NAME}"
                    descriptionTemplate ''
                }
            }
        }
    }
}

/**
 * Function that creates the Promote Job
 */
def createPromoteJob () {
  def nextJob="${APP_NAME}-Deploy-UAT"
  freeStyleJob("${TEAM}/${APP_NAME}/${APP_NAME}-Promote") {
        description("For QA to certify the version is ready for upper environments.")
        authorization {
            permissions("QA", [
                'hudson.model.Item.Build',
                'hudson.model.Item.Read',
                'hudson.model.Item.Cancel'
            ])


        }
        environmentVariables {
            env('IMAGE_NAME', "${APP_NAME}")
        }
        parameters {
            stringParam('IMAGE_VERSION', '', 'The version of the IMAGE to deploy')
        }
        steps {
            shell("""
                #!/bin/bash
                echo "Proceed to the next Stage."
            """.stripIndent().trim())
        }
        publishers {

          buildPipelineTrigger("${TEAM}/${APP_NAME}/${nextJob}") {
                parameters {
                    predefinedProp('IMAGE_VERSION', '${IMAGE_VERSION}')
                }
            }
        }

        properties {
            configure {
                it / 'properties'  <<  'se.diabol.jenkins.pipeline.PipelineProperty' {
                    taskName 'Certified by QA'
                    stageName 'PROMOTE'
                    descriptionTemplate 'Authorize Promotion to Upper Envs'
                }
            }
        }
    }
}

/**
 * Function that creates the UAT JOb
 */
def createUATJob() {
    def nextJob="${APP_NAME}-Deploy-PRODUCTION"
    freeStyleJob("${TEAM}/${APP_NAME}/${APP_NAME}-Deploy-UAT") {
        description("1. Update Task Definition.")

        authorization {
            permissions("ReleaseTeam", [
                'hudson.model.Item.Build',
                'hudson.model.Item.Read',
                'hudson.model.Item.Cancel'
            ])

        }

        environmentVariables {
            env('IMAGE_NAME', "${APP_NAME}")
            env('APP_ENV', 'uat01')
        }

        scm {

        }

        parameters {
            stringParam('IMAGE_VERSION', '', 'The version of the IMAGE to deploy')
        }

        steps {
            shell("echo deploy")
        }
        publishers {
            
               //automated tests
               downstreamParameterized {
                  trigger("${TEAM}/${APP_NAME}/${APP_NAME}-Test-UAT") {
                        condition('UNSTABLE_OR_BETTER')
                        parameters {
                            predefinedProp('IMAGE_VERSION', '${IMAGE_VERSION}')
                        }
                    }
                }
          
                buildPipelineTrigger("${TEAM}/${APP_NAME}/${nextJob}") {
                    parameters {
                        predefinedProp('IMAGE_VERSION', '${IMAGE_VERSION}')
                    }
                }
           
        }

        properties{
            configure {
                it / 'properties'  <<  'se.diabol.jenkins.pipeline.PipelineProperty' {
                    taskName 'Deploy to ${ENV,var="APP_ENV"}'
                    stageName "UAT"
                    descriptionTemplate """<b>Version:</b> \${ENV,var="IMAGE_VERSION"} """ 
                }
            }
        }
    }
}

/**
 * Function to create the Production Deploy Jobs
 *
 */
def createPRODJob() {
    freeStyleJob("${TEAM}/${APP_NAME}/${APP_NAME}-Deploy-PRODUCTION") {
        description("1. Update Task Definition.")

        authorization {
            permissions("ReleaseTeam", [
                'hudson.model.Item.Build',
                'hudson.model.Item.Read',
                'hudson.model.Item.Cancel'
            ])

        }

        environmentVariables {
            env('IMAGE_NAME', "${APP_NAME}")
            env('APP_ENV', 'prd01')
        }

        scm {
        }

        parameters {
            stringParam('IMAGE_VERSION', '', 'The version of the IMAGE to deploy')
        }

        steps {
            shell('''
echo get rollback version
ROLLBACK_VERSION="1.0.0.0"
echo "ROLLBACK_VERSION=${ROLLBACK_VERSION}" >> cicd_vars.txt
            ''')
            shell("echo deploy")
            environmentVariables {
                propertiesFile('$WORKSPACE/cicd_vars.txt')
            }

        }
        publishers {
            
               //automated tests
               downstreamParameterized {
                  trigger("${TEAM}/${APP_NAME}/${APP_NAME}-Test-PRODUCTION") {
                        condition('UNSTABLE_OR_BETTER')
                        parameters {
                            predefinedProp('IMAGE_VERSION', '${IMAGE_VERSION}')
                        }
                    }
                }
          
                buildPipelineTrigger("${TEAM}/${APP_NAME}/${APP_NAME}-Rollback-PRODUCTION") {
                    parameters {
                        predefinedProp('IMAGE_VERSION', '${ROLLBACK_VERSION}')
                    }
                }            

        }

        properties{
            configure {
                it / 'properties'  <<  'se.diabol.jenkins.pipeline.PipelineProperty' {
                    taskName 'Deploy to ${ENV,var="APP_ENV"}'
                    stageName "PRODUCTION"
                    descriptionTemplate """<b>Version:</b> \${ENV,var="IMAGE_VERSION"} """ 
                }
            }
        }
    }
}

/**
 * Function to create the Production Rollback Job
 *
 */
def createRollbackJob() {
    //def nextJob="${APP_NAME}-Deploy-TRAINING"
    freeStyleJob("${TEAM}/${APP_NAME}/${APP_NAME}-Rollback-PRODUCTION") {
        description("1. Update Task Definition.")

        authorization {
            permissions("ReleaseTeam", [
                'hudson.model.Item.Build',
                'hudson.model.Item.Read',
                'hudson.model.Item.Cancel'
            ])

        }

        environmentVariables {
            env('IMAGE_NAME', "${APP_NAME}")
            env('APP_ENV', 'prd01')
        }

        scm {

        }

        parameters {
            stringParam('IMAGE_VERSION', '', 'The version of the IMAGE to deploy')
        }

        steps {
            shell("echo deploy rollback version")
        }
        publishers {
               //automated tests
               downstreamParameterized {
                  trigger("${TEAM}/${APP_NAME}/${APP_NAME}-Test-ROLLBACK") {
                        condition('UNSTABLE_OR_BETTER')
                        parameters {
                            predefinedProp('IMAGE_VERSION', '${IMAGE_VERSION}')
                        }
                    }
                }
        }

        properties{
            configure {
                it / 'properties'  <<  'se.diabol.jenkins.pipeline.PipelineProperty' {
                    taskName 'Rollback'
                    stageName "ROLLBACK"
                    descriptionTemplate """<b>Version:</b> \${ENV,var="IMAGE_VERSION"} """
                }
            }
        }
    }
}

/**
 * Function to create the Deleivery Pipeline View
 */
def createPipeline() {
  deliveryPipelineView("${TEAM}/${APP_NAME}") {
        pipelineInstances(30)
        columns(1)
        sorting(Sorting.TITLE)
        updateInterval(4)
        enableManualTriggers()
        allowRebuild()
        allowPipelineStart()
        showDescription()
        showPromotions()
        showAvatars()
        showChangeLog()
        pipelines {
          component("${APP_NAME}", "${TEAM}/${APP_NAME}/${APP_NAME}-Build")
        }
    }
}
