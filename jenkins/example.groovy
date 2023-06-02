pipeline {
  environment {
    imagename = "yurix"
    registry = "_.dkr.ecr.eu-central-1.amazonaws.com" # account hidden
    ecrurl = "https://_.dkr.ecr.eu-central-1.amazonaws.com"
    ecrcredentials = "ecr:eu-central-1:ecr"
    TOKEN = credentials('token')
  }
  agent any
  parameters {
    string(defaultValue: 'master', name: 'BRANCH', description: 'branch to pull')
    string(defaultValue: 'demo', name: 'NAMESPACE', description: 'Namespace to deploy')
    choice(name: 'BRANCH_OPS', choices: ['master'], description: 'DevOps branch')
  }
  stages {
    stage('Cloning Git') {
        steps {
          sh 'rm -rf ./workspace/*'
          git credentialsId: 'github', branch: "${BRANCH}",
            url: 'git@github.com:aldebober/webapp.git'
        }
    }
    stage('Building image') {
      steps{
        sh 'mv docker/Dockerfile .'
        script {
            docker.withRegistry(ecrurl, ecrcredentials) {
                dockerImage = docker.build imagename
            }
        }
      }
    }
    stage('Push Image') {
      steps{
        script {
          docker.withRegistry(ecrurl, ecrcredentials) {
            dockerImage.push("${BRANCH}-$BUILD_NUMBER")
          }
        }
      }
    }
    stage('Deploying image') {
      steps{
        sh """helm upgrade --install -n \"${params.NAMESPACE}\" \
            --set image.repository=_.dkr.ecr.eu-central-1.amazonaws.com/yurix --set image.tag=\"${BRANCH}-$BUILD_NUMBER\" \
            yurix helm -f helm/values-dev-eu-1.yaml  --debug
        }
    }
    stage('Cleaning up') {
      steps{
        sh "echo Deployed image: $imagename:${BRANCH}-$BUILD_NUMBER"
        sh "docker rmi $registry/$imagename:${BRANCH}-$BUILD_NUMBER"
      }
    }
  }
}

