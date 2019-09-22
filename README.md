# play-docka

This is a demo web app for AWS and GCP. Check [cfn](https://github.com/malliina/cfn) for AWS CloudFormation templates that use
this app.

## AWS

The buildspec files in the root of the repo can be used with AWS CodeBuild and CodePipeline as follows:

| Buildspec file | Deployment target
|----------------|------------------
| [buildspec.yml](buildspec.yml) | Single-container Elastic Beanstalk
| [buildspec-ecr.yml](buildspec-ecr.yml) | Multi-container Elastic Beanstalk
| [buildspec-ecs.yml](buildspec-ecs.yml) | ECS with Fargate

## GCP

[Cloud Build](https://cloud.google.com/cloud-build/) can be used to deploy the application to 
[App Engine](https://cloud.google.com/appengine/). The following files are used:

| Cloud build file | Meaning
|------------------|---------
| [cloudbuild.build.yaml](cloudbuild.build.yaml) | Build instructions for the build image
| [cloudbuild.yaml](cloudbuild.yaml) | Deployment instructions for the application

To set up continuous deployment:

1. Grant App Engine access to the Cloud Build service account as described here: 
https://cloud.google.com/source-repositories/docs/quickstart-triggering-builds-with-source-repositories.
1. Run [cloudbuild.build.yaml](cloudbuild.build.yaml) once using [Cloud Build](https://cloud.google.com/cloud-build/) to 
push a build image to [Container Registry](https://cloud.google.com/container-registry/).
1. Create a Cloud Build trigger that runs [cloudbuild.yaml](cloudbuild.yaml) on code changes to your GitHub repo. This
will deploy the application to App Engine.
