node {
  git url: 'https://github.com/jenkinsci/aws-codebuild-plugin.git'
  sh "mvn clean install"
  archiveArtifacts artifacts: 'target/aws-codebuild.*', fingerprint: true
}
