# webapp

# Description

Check task:
```
curl -X POST -H "NotBad: true" https://yurix.tradingdev.io
```
Yes, it's not 8089 port, only pod use it, but technically it works also locally:
```
k port-forward po/yurix-dev-7cfb5bfc47-kl5wz 8089:8089
```

The app was deployed in current env in EKS v1.23 with helm my working [chart](./helm/Chart.yaml)
Image files in docker foilder:
 - [Dockerfile](./docker/Dockerfile)
 - [amazing app](./docker/webapp.go)

Example pipeline [groovy](./jenkins/example.groovy)
Sorry, I'm not good in CI/CD so I took my pipeline for old project on PHP for one client and customized it
Jenkins was deployed with helm on the same cluster
```
helm install jenkins jenkins/jenkins
```
and then there were installed plugin and added credentials

## Manifests

Helm [values](./helm/values-dev-eu-1.yaml)

Deployment with one replica and NodePort svc:

```
k get all -l app=yurix
NAME                             READY   STATUS    RESTARTS   AGE
pod/yurix-dev-7cfb5bfc47-kl5wz   1/1     Running   0          110m

NAME            TYPE       CLUSTER-IP       EXTERNAL-IP   PORT(S)        AGE
service/yurix   NodePort   172.20.125.246   <none>        80:30618/TCP   127m

NAME                        READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/yurix-dev   1/1     1            1           126m

NAME                                   DESIRED   CURRENT   READY   AGE
replicaset.apps/yurix-dev-7cfb5bfc47   1         1         1       110m
replicaset.apps/yurix-dev-8549db7db6   0         0         0       126m
```

Deployment manifest doesn't have any Security context, FS is writable, no restriction for user and etc etc..
Lot's of work to be done in here..

```
k describe deploy yurix-dev
Name:                   yurix-dev
Namespace:              demo
CreationTimestamp:      Fri, 02 Jun 2023 11:45:43 +0300
Labels:                 app=yurix
                        app.kubernetes.io/managed-by=Helm
                        chart=yurix-0.1.8
                        heritage=Helm
                        release=v0.0.1
                        tags.datadoghq.com/version=1.0.0
Annotations:            deployment.kubernetes.io/revision: 2
                        meta.helm.sh/release-name: yurix
                        meta.helm.sh/release-namespace: demo
Selector:               app=yurix
Replicas:               1 desired | 1 updated | 1 total | 1 available | 0 unavailable
StrategyType:           RollingUpdate
MinReadySeconds:        0
RollingUpdateStrategy:  25% max unavailable, 25% max surge
Pod Template:
  Labels:       app=yurix
                env=dev
                release=v0.0.1
                role=api
  Annotations:  prometheus.io/path: /metrics
                prometheus.io/port: 8089
                prometheus.io/scrape: true
  Containers:
   yurix:
    Image:      _.dkr.ecr.eu-central-1.amazonaws.com/yurix:v0.0.1
    Port:       8089/TCP
    Host Port:  0/TCP
    Limits:
      cpu:     30m
      memory:  50Mi
    Requests:
      cpu:     10m
      memory:  10Mi
    Environment:
      DD_LOGS_INJECTION:  true
      DD_VERSION:          (v1:metadata.labels['tags.datadoghq.com/version'])
      DD_AGENT_HOST:       (v1:status.hostIP)
      DD_ENTITY_ID:        (v1:metadata.uid)
    Mounts:               <none>
  Volumes:                <none>
Conditions:
  Type           Status  Reason
  ----           ------  ------
  Available      True    MinimumReplicasAvailable
  Progressing    True    NewReplicaSetAvailable
OldReplicaSets:  <none>
NewReplicaSet:   yurix-dev-7cfb5bfc47 (1/1 replicas created)
Events:          <none>

```
Created separated ALB balancer for my app:

```
 k describe ingress -l app=yurix
Name:             yurix
Namespace:        demo
Address:          k8s-testdeveu1-7beb5fa052-1422077610.eu-central-1.elb.amazonaws.com
Default backend:  default-http-backend:80 (<error: endpoints "default-http-backend" not found>)
Rules:
  Host                 Path  Backends
  ----                 ----  --------
  yurix.tradingdev.io
                       /*   yurix:80 (10.0.10.110:8089)
Annotations:           alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:eu-central-1:_
                       alb.ingress.kubernetes.io/group.name: test-dev-eu-1
                       alb.ingress.kubernetes.io/healthcheck-path: /
                       alb.ingress.kubernetes.io/scheme: internet-facing
                       alb.ingress.kubernetes.io/security-groups: sg-02xxxxxxxa6
                       alb.ingress.kubernetes.io/subnets: list_of_public_subnets
                       alb.ingress.kubernetes.io/success-codes: 200-499
                       alb.ingress.kubernetes.io/target-type: ip
                       kubernetes.io/ingress.class: alb
                       meta.helm.sh/release-name: yurix
                       meta.helm.sh/release-namespace: demo
Events:                <none>

```

## Env

The app requires only EKS in AWS with VPC in account
EKS was deployed with TF module
```
module "my_cluster" {
  source                                         = "terraform-aws-modules/eks/aws"
  version                                        = "17.18.0"
  cluster_name                                   = "${var.id}-${terraform.workspace}-eks"
  cluster_version                                = "1.23"
  subnets                                        = flatten([data.terraform_remote_state.networking.outputs.private_subnets, data.terraform_remote_state.networking.outputs.public_subnets])
  vpc_id                                         = data.terraform_remote_state.networking.outputs.vpc_id
  eks_oidc_root_ca_thumbprint                    = var.oidc_thumbprint
  cluster_endpoint_private_access                = var.endpoint_private_access[terraform.workspace]
  cluster_endpoint_private_access_cidrs          = [data.terraform_remote_state.networking.outputs.vpc_cidr]
  cluster_create_endpoint_private_access_sg_rule = true
  cluster_endpoint_public_access                 = var.endpoint_public_access[terraform.workspace]
  cluster_enabled_log_types                      = ["api", "audit", "authenticator", "controllerManager", "scheduler"]

  cluster_encryption_config = [
    ...
  ]

    app-group = {
      desired_capacity = var.app_desired_capacity[terraform.workspace]
      max_capacity     = 25
      min_capacity     = var.app_min_size[terraform.workspace]
      subnets          = data.terraform_remote_state.networking.outputs.private_subnets
      name             = "${var.id}-${terraform.workspace}-app-group"
      instance_types   = [var.instance_type[terraform.workspace]]

      launch_template_id      = aws_launch_template.eks_lt_app.id
      launch_template_version = aws_launch_template.eks_lt_app.latest_version

      k8s_labels = {
        Environment = terraform.workspace
      }
      additional_tags = {
        Name = "${var.id}-${terraform.workspace}-app-asg"
      }
    }
    ...
}

resource "aws_launch_template" "eks_lt_app" {
  name                   = "${var.id}-${terraform.workspace}-app-node"
  key_name               = var.sshkey[terraform.workspace]
  update_default_version = true

  block_device_mappings {
    device_name = "/dev/sda1"
    ...
    }
  }

  # https tokens is requiered option for security
  metadata_options {
    http_endpoint = "enabled"
    # http_tokens                 = "optional"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  tag_specifications {
    resource_type = "instance"
    ...
  }
}

```
