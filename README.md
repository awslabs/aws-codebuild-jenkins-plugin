# AWS CodeBuild Jenkins Plugin
## Setup Jenkins
If you already have a Jenkins server on which you want to use the plugin, you can skip to the Plugin Installation section. If not, you can either:

1. Download Jenkins at [jenkins.io](https://jenkins.io) and run it directly with `java -jar jenkins.war`.
2. Create a Jenkins server on Amazon EC2 with [AWS Marketplace](https://aws.amazon.com/marketplace/search/results?searchTerms=jenkins&x=0&y=0&page=1&ref_=nav_search_box). 

## Plugin Installation

1. Search for `AWS CodeBuild Plugin for Jenkins` in the Plugin Manager on your Jenkins instance **or**

2. Install the plugin manually: 

	* Build the AWS CodeBuild Jenkins plugin locally by running `mvn install` to generate `aws-codebuild.hpi` in the `target` 	directory **or**
	* Download the latest `aws-codebuild.hpi` directly from the [Jenkins plugin repository](https://plugins.jenkins.io/aws-codebuild)

	* In Jenkins, choose **Manage Jenkins** > **Manage Plugins** > **Advanced** > **Upload Plugin** > **Browse** (select the 	*aws-codebuild.hpi* file) > **Upload** to install the AWS CodeBuild plugin.

## Plugin Usage

### Using AWS CodeBuild with source available outside of your VPC

1. [Create Project](http://docs.aws.amazon.com/console/codebuild/create-project) on the AWS CodeBuild console.
	* Switch to the region you would prefer to run the build in.
	* Make sure to write down the project's name.
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

### Using AWS CodeBuild with source only available inside of your VPC

To use AWS CodeBuild inside of a VPC the Jenkins plugin is going to pull the source from your repository inside of your VPC, zip it up and place the source into the Amazon S3 input bucket for the project you specified. To do this we need to make some modifications to the setup above.


1. [Create an Amazon S3 bucket](http://docs.aws.amazon.com/AmazonS3/latest/gsg/CreatingABucket.html).
	* Bucket must be [versioned](http://docs.aws.amazon.com/AmazonS3/latest/dev/Versioning.html).

1. [Create Project](http://docs.aws.amazon.com/console/codebuild/create-project) on the AWS CodeBuild console.
	* Use Amazon S3 as the source type.
		*  Use the bucket you created previously.
		*  Specifiy an Amazon S3 object key.
	* Make sure to write down the project's name.
2. Create AWS IAM user to be used by the Jenkins plugin.
	* [Create a policy](https://console.aws.amazon.com/iam/home?region=us-east-1#/policies$new) similar to the one following this section.
	* Go to the [AWS IAM console](https://console.aws.amazon.com/iam/home?region=us-east-1#/users$new?step=details), and create a new user.
		* Access type should be: Programmatic Access.
		* Attach policy to user that you created previously.
3. Create a freestyle project in Jenkins.
  * For the **Source Code Management** make sure to select how you would like to retrieve your source. You may need to install the [GitHub Plugin](https://wiki.jenkins-ci.org/display/JENKINS/GitHub+Plugin) to your Jenkins server.
  * On the Configure page, choose **Add build step** > **Run build on AWS CodeBuild**. 
  * Configure the build step.
     * Enter **Region**, **Credentials** from the user created previously, and **Project name**.
     * Select **Use Jenkins source**.
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
awsCodeBuild projectName: "project", awsAccessKey: AWS_ACCESS_KEY_ID, awsSecretKey: AWS_SECRET_ACCESS_KEY, region: "us-west-2", sourceControlType: "jenkins"
```

## Contributions

Want to contribute a bug fix or feature? See [CONTRIBUTING.md](CONTRIBUTING.md) for instructions.
