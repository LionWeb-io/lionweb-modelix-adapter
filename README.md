# LionWeb to Modelix Adapter

This tool provides a minimalistic LionWeb REST bulk API.
Requests to this adapter are forwarded to a modelix `model-server` which is used as the underlying repository.

Check out https://modelix.org/ for more information about modelix.

## How to build

Run the following in the root of this project:

```shell
$ ./gradlew build
```

## How to run

### From Source

Generally, you will need a running `model-server` to which this adapter will connect.

For testing and development, you can avoid the manual setup of a `model-server`.
You can simply set the `modelix.server.embedded` option in the [application.conf](src/main/resources/application.conf) to `true` (set by default).

Thus, you can simply run 

```shell
$ ./gradlew run
```

For more elaborated setups, please consult the [modelix documentation](https://docs.modelix.org/modelix/main/core/howto/usage-model-server.html) on how to start a dedicated `model-server`.
Once the `model-server` is up and running, you can configure this application to connect to it.

## Usage

Once the adapter is running, check the included swaggerUI over at http://127.0.0.1:28102/swagger to get an overview of the provided REST endpoints.

## Assumptions / TODOs

This implementation is an early draft.
The following assumptions and todos currently remain open:

* This implementation is stateless. As a result, performance will be bad for large models. If your models are too big or queries take too long, consider using the modelix `model-server` directly via its SDKs and APIs (e.g. the [replicated model](https://docs.modelix.org/modelix/main/core/howto/usage-model-client-v2.html), [ModelQL](https://docs.modelix.org/modelix/main/core/howto/modelql.html), or the [REST API](http://127.0.0.1:28101/swagger))
* This adapter does not support advanced `model-server` features, such as real-time collaboration, branches, or repositories. Instead an opinionated mapping from LionWeb specification to modelix `model-api` is implemented. 
* Reference cardinality: Currently references with cardinality beyond `1:1` are not supported
* Language structures are not supported. The corresponding fields `key` and `version` will always be `null`
* `2023.1` is the only supported `serializationFormatVersion`
* Docker container builds are missing
* Test coverage is very low and/or are missing



[//]: # (### Using Docker)

[//]: # ()
[//]: # (This project also creates a docker container during the build.)

[//]: # (Once you build the project, the docker container is locally available, you can run it via)

[//]: # ()
[//]: # (```shell)

[//]: # ($ TODO)

[//]: # (```)
