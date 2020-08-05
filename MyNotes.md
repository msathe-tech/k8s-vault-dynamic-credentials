1. Change vault service name from default to vault ns
2. helm install vault hashicorp/vault -f ../k8s-toolbox/vault/values.yaml -n vault
3. Create configMap before deploying the app
