# Using Vault dynamic credentials with Spring Boot deployed to Kubernetes

This project shows how to use [HashiCorp Vault](https://www.vaultproject.io)
to manage dynamic database credentials with a
[Spring Boot](https://spring.io/projects/spring-boot) app deployed to
Kubernetes.

These credentials are not known by the app or even set in Kubernetes descriptors:
Vault will take care of generating credentials, and make sure these are
periodically updated.
Using [Spring Cloud Vault](https://cloud.spring.io/spring-cloud-vault/reference/html/)
you can leverage Vault in your app without having to write any code:
you'll be able to securely connect to your database.

## Deploying this app to Kubernetes

### Creating a Docker image

Create a Docker image using `pack` CLI from
[Cloud Native Buildpacks](https://buildpacks.io):
```bash
$ pack build myuser/k8s-vault-dynamic-credentials --publish
```

Replace `myuser` with your Docker ID: this image will be pushed to
[Docker Hub](https://hub.docker.com).

Of course, you could build a Docker image the old way using a `Dockerfile`,
but I'm a lazy person: just let Cloud Native Buildpacks build a secure Docker
image for you!

You may skip this step, by using the already built Docker image available
under `alexandreroman/k8s-vault-dynamic-credentials`.

### Deploying a PostgreSQL database to Kubernetes

I assume you have access to a Kubernetes cluster.

Use [Helm](https://helm.sh) to deploy a PostgreSQL database instance:
```bash
$ helm upgrade mydb stable/postgresql \
    --set postgresqlPassword=mysupersecretpassword \
    --set postgresqlDatabase=mydb \
    --timeout 600 --install
```

The database is named `mydb`: admin credentials are `postgres` / `mysupersecretpassword`.

### Deploying the Spring Boot app

Use these Kubernetes descriptors to deploy a sample app:
```bash
$ kubectl apply -f k8s
```

This app will not work until you set up Vault to manage database credentials:
let's do this.

### Deploying Vault to Kubernetes

[Use these instructions](https://github.com/alexandreroman/k8s-toolbox/tree/master/vault)
to deploy a Vault instance to your Kubernetes cluster.

Make sure everything works by opening a connection to your Vault instance:
```bash
$ kubectl port-forward vault-0 8200
```

Connect to your Vault instance: 
```bash
$ export VAULT_ADDR=http://127.0.0.1:8200
$ vault status
Key             Value
---             -----
Seal Type       shamir
Initialized     true
Sealed          false
Total Shares    1
Threshold       1
Version         1.2.1
Cluster Name    vault-cluster-757e0511
Cluster ID      17deeeb1-0523-a92d-911f-64aa46625f52
HA Enabled      false
```

You should be able to log in using the `root` token
(please remember this Vault instance is running in development mode):
```bash
$ vault login
Token (will be hidden): root
Success! You are now authenticated. The token information displayed below
is already stored in the token helper. You do NOT need to run "vault login"
again. Future Vault requests will automatically use this token.

Key                  Value
---                  -----
token                root
token_accessor       8pOjQwkoInyYtOQU34sPWHsJ
token_duration       ∞
token_renewable      false
token_policies       ["root"]
identity_policies    []
policies             ["root"]
```

### Enabling Vault access

You now have a running Vault instance, an empty PostgreSQL database instance,
and an idle Spring Boot app: let's connect the dots!

The Spring Boot app will connect to the Vault instance by authenticating using the
[`approle`](https://www.vaultproject.io/docs/auth/approle.html) method:
```bash
$ vault auth enable approle
Success! Enabled approle auth method at: approle/
```

Use this policy to allow database credentials access:
```bash
$ vault policy write k8s-vault-dynamic-credentials k8s/app-policy.hcl
Success! Uploaded policy: k8s-vault-dynamic-credentials
```

Create approle credentials:
```bash
$ vault write auth/approle/role/k8s-vault-dynamic-credentials-role \
      secret_id_ttl=30m \
      token_num_uses=10 \
      token_ttl=30m \
      token_max_ttl=60m \
      secret_id_num_uses=40 \
      policies="k8s-vault-dynamic-credentials"
Success! Data written to: auth/approle/role/k8s-vault-dynamic-credentials-role
```

Get the `role-id` credential:
```bash
$ vault read auth/approle/role/k8s-vault-dynamic-credentials-role/role-id
Key        Value
---        -----
role_id    716cbb29-fc91-a4dd-b772-8713d5b3b37f
```

You need `role-id` and `secret-id` credentials to authenticate your
Spring Boot app with Vault. Let's generate a `secret-id`:
```bash
$ vault write -f auth/approle/role/k8s-vault-dynamic-credentials-role/secret-id
Key                   Value
---                   -----
secret_id             9f05d1cf-89e7-20f5-58d4-997595dd6731
secret_id_accessor    76efa1ab-18d0-02f2-fe5a-d6af6bbe95f1
```

Beware that `secret-id` credentials will expire in 30 min
(see parameter `secret_id_ttl` used when you created the `role-id`):
you may have to generate a new value if you deploy a new app instance.

Keep `role-id` and `secret-id` credentials handy:
you will need these values in a couple of minutes.

### Configuring Vault to manage database credentials

Create a Vault database secrets engine:
```bash
$ vault secrets enable database
Success! Enabled the database secrets engine at: database/
```

Configure Vault database access:
```bash
$ vault write database/config/mydb \
      plugin_name=postgresql-database-plugin \
      allowed_roles="mydb-role" \
      connection_url="postgresql://{{username}}:{{password}}@mydb-postgresql.default.svc.cluster.local:5432/mydb?sslmode=disable" \
      username="postgres" \
      password="mysupersecretpassword"
```

Vault will use this configuration to connect to the database.
Only Vault is aware of the admin password, not your apps.

Configure database credentials generation:
```bash
$ vault write database/roles/mydb-role \
      db_name=mydb \
      creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
          GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO \"{{name}}\";" \
      default_ttl="5m" \
      max_ttl="24h"
Success! Data written to: database/roles/mydb-role
```

At this point, Vault will automatically create credentials when an app is requesting
database access: these credentials will expire after 5 min.

As you can see, you can use these credentials to connect to your database:
```bash
$ vault read database/creds/mydb-role
Key                Value
---                -----
lease_id           database/creds/mydb-role/uIbLkdxJD4YjzCdDmGtBPhQs
lease_duration     5m
lease_renewable    true
password           A1a-W4YhHBvALXmcv0Ci
username           v-token-mydb-rol-60JlLhdh2TyQwypqh6MY-1566290708
```

### Last step: configuring Spring Cloud Vault

Create file `bootstrap-kubernetes.yml`:
```bash
$ cp k8s/bootstrap-kubernetes.template bootstrap-kubernetes.yml
```

Edit this file, and set `role-id` and `secret-id` credentials, and the Vault URL:
```yaml
spring:
  cloud:
    vault:
      enabled: true
      # Set path to your Vault service instance.
      uri: http://vault.default.svc.cluster.local:8200
      authentication: APPROLE
      app-role:
        # Insert Vault role-id and secret-id here.
        role-id: 716cbb29-fc91-a4dd-b772-8713d5b3b37f
        secret-id: 9f05d1cf-89e7-20f5-58d4-997595dd6731
        role: k8s-vault-dynamic-credentials-role
      generic:
        # Disable secret store access since it's not used in this configuration.
        enabled: false
      database:
        # Enable database dynamic credentials. 
        enabled: true
        role: mydb-role
        backend: mydb
```

Create file `application-kubernetes.yml`:
```bash
$ cp k8s/application-kubernetes.yml.template application-kubernetes.yml
```

Edit this file, and set the database URL:
```yaml
spring:
  datasource:
    # Set path to your PostgreSQL database instance.
    url: "jdbc:postgresql://mydb-postgresql.default.svc.cluster.local/mydb"
    # You do not need to set database credentials here:
    # Spring Cloud Vault will take care of setting username/password properties
    # by retrieving credentials from Vault when the app starts.
```

Deploy these configuration files to your cluster:
```bash
$ kubectl -n k8s-vault-dynamic-credentials create configmap app \
    --from-file bootstrap-kubernetes.yml --from-file application-kubernetes.yml
```

## See Vault dynamic credentials in action

Check your app is running fine:
```bash
kubectl -n k8s-vault-dynamic-credentials get pods
NAME                   READY   STATUS    RESTARTS   AGE
app-596cb5465f-lp4zq   1/1     Running   0          29m
```

Get the allocated IP address to access this app:
```bash
kubectl -n k8s-vault-dynamic-credentials get svc       
NAME     TYPE           CLUSTER-IP       EXTERNAL-IP   PORT(S)        AGE
app-lb   LoadBalancer   10.100.200.113   34.77.95.55   80:30975/TCP   173m
```

This app allows you to create random superhero instances using endpoint `/new`:
```bash
$ curl -X POST 34.77.95.55/new -w '\n'
{"name":"Ultra Magneto","power":"Heat Resistance","created":"2019-08-20T14:44:36.329+0000"}
```

A new entry is added to SQL table `superhero`.
Check that this superhero is properly stored in the database:
```bash
$ curl -s 34.77.95.55 | jq -r
[
  {
    "name": "Ultra Magneto",
    "power": "Heat Resistance",
    "created": "2019-08-20T14:44:36.329+0000"
  },
  {
    "name": "Red Quantum",
    "power": "Pyrokinesis",
    "created": "2019-08-20T14:44:34.393+0000"
  },
  {
    "name": "Bolt",
    "power": "Animal Oriented Powers",
    "created": "2019-08-20T14:44:32.509+0000"
  },
  {
    "name": "Giant Lobo IX",
    "power": "Symbiote Costume",
    "created": "2019-08-20T14:44:31.633+0000"
  },
  {
    "name": "Cable",
    "power": "Energy Armor",
    "created": "2019-08-20T14:44:30.554+0000"
  },
  {
    "name": "Dash Boy",
    "power": "Stealth",
    "created": "2019-08-20T14:44:29.835+0000"
  },
  {
    "name": "Red Nathan Petrelli",
    "power": "Technopath/Cyberpath",
    "created": "2019-08-20T14:44:29.045+0000"
  },
  {
    "name": "General Toad Fist",
    "power": "Phasing",
    "created": "2019-08-20T14:44:27.898+0000"
  }
]
```

## Recap

Using Vault, you can manage service credentials from a single point.
Your apps don't need to embed credentials, and you don't need to declare
these credentials in your Kubernetes descriptors (using environment variables or files).

Use Spring Cloud Vault to leverage Vault with your Spring Boot apps: just add a few
configuration properties to connect your app to Vault, and the framework will
take care of retrieving credentials as needed.

## Contribute

Contributions are always welcome!

Feel free to open issues & send PR.

## License

Copyright &copy; 2019 [Pivotal Software, Inc](https://pivotal.io).

This project is licensed under the [Apache Software License version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
