# AWS CodeBuild Jenkins Plugin
The AWS CodeBuild plugin for Jenkins provides a build step for your Jenkins project.

![Build Status](https://codebuild.us-west-2.amazonaws.com/badges?uuid=eyJlbmNyeXB0ZWREYXRhIjoiK0hKUGVGdFlLS0ZmWTY3TnpIaitFcHZydlg1THlsK1dYNGN4dEtxSHZPQzBna0EwWkwzY3JQMUdGaGF3THVkd3NSYmFKT2NmOFRaNmFTak9Ma1VZd0xzPSIsIml2UGFyYW1ldGVyU3BlYyI6IlVvZ0Fpc3NvdGxLY002UjIiLCJtYXRlcmlhbFNldFNlcmlhbCI6MX0%3D&branch=master) [![license](http://img.shields.io/badge/license-Apache2.0-brightgreen.svg?style=flat)](https://github.com/jenkinsci/aws-codebuild-plugin/blob/master/LICENSE)
[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/aws-codebuild-plugin/master)](https://ci.jenkins.io/job/Plugins/job/aws-codebuild-plugin/job/master/)

## Plugin Installation
If you already have a Jenkins setup and would like to only install the AWS CodeBuild plugin, then the recommended approach would be to search for "AWS CodeBuild" in the Plugin Manager on your Jenkins instance.

We have also written a blog post for setting up a new Jenkins server with AWS CloudFormation and integrating it with AWS CodeBuild and AWS CodeDeploy. Learn more: https://aws.amazon.com/blogs/devops/setting-up-a-ci-cd-pipeline-by-integrating-jenkins-with-aws-codebuild-and-aws-codedeploy

## Plugin Usage

### Using AWS CodeBuild with source available outside of your VPC

1. [Create Project](http://docs.aws.amazon.com/console/codebuild/create-project) on the AWS CodeBuild console.
	* Switch to the region you would prefer to run the build in.
	* You can optionally set the Amazon VPC configuration to allow CodeBuild build container to access resources within your VPC.
	* Make sure to write down the project's name.
	* (Optional) If your source repository is not natively supported by CodeBuild, you can set the input source type for your project as S3 for the CodeBuild project.
2. Create AWS IAM user to be used by the Jenkins plugin.
	* [Create a policy](https://console.aws.amazon.com/iam/home?region=us-east-1#/policies$new) similar to the one following this section.
	* Go to the [IAM console](https://console.aws.amazon.com/iam/home?region=us-east-1#/users$new?step=details), and create a new user.
		* Access type should be: Programmatic Access.
		* Attach policy to user that you created previously.
3. Create a freestyle project in Jenkins.
	* On the Configure page, choose **Add build step** > **Run build on AWS AWS CodeBuild**.
	* Configure your build step.
		* Enter **Region**, **Credentials** from the user created previously, and **ProjectName**.
		* Select **Use Project source**.
		* Save the configuration and run a build from Jenkins.

4. For the Source Code Management make sure to select how you would like to retrieve your source. You may need to install the GitHub Plugin (or the relevant source repository provider's Jenkins plugin) to your Jenkins server.
	* On the Configure page, choose Add build step > Run build on AWS CodeBuild.
Configure the build step.
	* Enter Region, Credentials from the user created previously, and Project name.
	* Select Use Jenkins source.
	* Save the configuration and run a build from Jenkins.

Policy sample for IAM user:
```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Resource": ["arn:aws:logs:{{region}}:{{awsAccountId}}:log-group:/aws/codebuild/{{projectName}}:*"],
            "Action": ["logs:GetLogEvents"]
        },
        {
            "Effect": "Allow",
            "Resource": ["arn:aws:s3:::{{inputBucket}}"],
            "Action": ["s3:GetBucketVersioning"]
        },
        {
            "Effect": "Allow",
            "Resource": ["arn:aws:s3:::{{inputBucket}}/{{inputObject}}"],
            "Action": ["s3:PutObject"]
        },
        {
            "Effect": "Allow",
            "Resource": ["arn:aws:s3:::{{outputBucket}}/*"],
            "Action": ["s3:GetObject"]
        },
        {
            "Effect": "Allow",
            "Resource": ["arn:aws:codebuild:{{region}}:{{awsAccountId}}:project/{{projectName}}"],
            "Action": ["codebuild:StartBuild",
                       "codebuild:BatchGetBuilds",
                       "codebuild:BatchGetProjects"]
        }
	]
}
```
### Using the AWS CodeBuild plugin with the Jenkins Pipeline plugin

Use the snippet generator (click "Pipeline Syntax" on your pipeline project page) to generate the pipeline script that adds CodeBuild as a step in your pipeline. It should generate something like

```
awsCodeBuild projectName: 'project', credentialsType: 'keys', region: 'us-west-2', sourceControlType: 'jenkins'
```

Additionally, this returns a result object which exposes the following methods which can be useful to later steps:

* `getBuildId()`: returns the build ID of the build (similar to `codebuild-project-name:12346789-ffff-0000-aaaa-bbbbccccdddd`)
* `getArn()`: returns the ARN of the build (similar to `arn:aws:codebuild:AWS_REGION:AWS_ACCOUNT_ID:build/CODEBUILD_BUILD_ID`, where `CODEBUILD_BUILD_ID` is the same information returned in getBuildId)
* `getArtifactsLocation()`: returns the S3 ARN of the artifacts location (similar to `arn:aws:s3:::s3-bucket-name/path/to/my/artifacts`)

### AWS Credentials in Jenkins

It's recommended to use the Jenkins credentials store for your AWS credentials. Your Jenkins credentials must be of type `CodeBuild Credentials` to be compatible with the CodeBuild plugin. When creating new `CodeBuild Credentials`, the plugin will attempt to use the [default credentials provider chain](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html) if AWS access and secret keys are not defined. You can also specify your AWS access and secret keys and session token in the CodeBuild configuration when using `credentialsType: 'keys'`. Example: 

```
awsCodeBuild projectName: 'project', 
             credentialsType: 'keys',
	     awsAccessKey: env.AWS_ACCESS_KEY_ID,
	     awsSecretKey: env.AWS_SECRET_ACCESS_KEY,
	     awsSessionToken: env.AWS_SESSION_TOKEN,
	     ...
```

If the access/secret keys and session token are not specified, the plugin will attempt to use the default credentials provider chain. When running a Jenkins pipeline build, the plugin will attempt to use credentials from the [pipeline-aws](https://plugins.jenkins.io/pipeline-aws) plugin before falling back to the default credentials provider chain. If you are running Jenkins on an EC2 instance, leave the access and secret key fields blank and specify `credentialsType: 'keys'`to use credentials from your EC2 instance profile, which is in the default credentials provider chain. 

