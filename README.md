Test of the WebFlux large JSON response issue with a vanilla Spring app
=============================================

See https://github.com/opentable/webflux-large-response-otj for more information.

This is a pure vanilla spring implementation with no OTJ components.

# Mesos test
1) To build a docker image to deploy to OT mesos:

```
$ mvn package && TIMESTAMP=$(date "+%Y%m%d%H%M%S") && otpl-package-docker webflux-large-response-spring webflux-large-response-spring-0.1.0-$TIMESTAMP target/webflux-large-response-spring-0.1.0.jar

$ otpl-deploy pp-sf webflux-large-response-spring-0.1.0-$TIMESTAMP
```


# Local test
1) To run in a local docker container:

```
$ mvn package

$ docker build -t docker.otenv.com/webflux-large-response-spring .

$ docker push docker.otenv.com/webflux-large-response-spring

$ docker run -p 8080:8080 docker.otenv.com/webflux-large-response-spring
```

2) Grab the hostname

```
GCHOST=0.0.0.0:8080
```

3) Curl the service

```
$ curl -v $GCHOST/?path="`otpl-discover pp-sf service-aggregator-guestcenter`/restaurant/983800/availabilityplan/6584fa61-4fca-469c-8a60-018a432fb8f6"
```

