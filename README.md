A demonstration of the Spring WebFlux & Jetty large response cut off issue affecting Spring 5.1.6 and above
===========================================================================================================

# I. Description

Given a Spring WebFlux application running on Jetty server using Spring 5.1.6 / Spring Boot 2.1.5 and above:

When returning a response from the server that is sufficiently large*, the connection is closed prematurely and 
the response is cut off part-way through.

The client will only receive the bytes of the response prior to the cut off point. The server will emit a
Jetty error, `java.io.IOException: Closed while Pending/Unready`.

```
2019-12-03 20:28:13.862  WARN 1 --- [ttp@6bb4dd34-15] org.eclipse.jetty.server.HttpOutput      : java.io.IOException: Closed while Pending/Unready
```

\* What is a "sufficiently large" response to trigger the cut-off depends on various factors detailed below.

This repository is a demonstration of the issue that can be used to reproduce when running locally from IDE,
in a local Docker container, in Docker container deployed to Heroku, or running on some other container 
resource cluster (e.g. Mesos).

See usage details in the Background & Analysis section below.


# II. Affected Versions

- Spring 5.1.6 / Spring Boot 2.1.5 and above (tested up to Spring 5.2.1/Boot 2.2.1)
- Jetty 9.4.15.v20190215 and above (tested up to Jetty 9.4.24.v20191120)
- Java 8 and above (tested up to Java 11)

Note that the issue is only related to the Spring/Boot version and not the Jetty or Java versions.

We tested various combinations of Spring, Spring Boot, Jetty, and Java versions and determined that
the issue is introduced only with an update to Spring 5.1.7/Spring Boot 2.1.5, regardless of which Jetty or Java
version is used.

The issue is *not* exhibited on Spring 5.1.5/Spring Boot 2.1.4, regardless of which Jetty or Java version is used.


## Tests with different Spring and Jetty versions

_Does the issue occur?_

--

Boot 2.1.4, Jetty 9.4.15.v20190215: No (This is the default pairing for Spring Boot 2.1.4)

Boot 2.1.4, Jetty 9.4.16.v20190411: No

Boot 2.1.4, Jetty 9.4.17.v20190418: No

Boot 2.1.4, Jetty 9.4.18.v20190429: No

Boot 2.1.4, Jetty 9.4.19.v20190610: No

Boot 2.1.4, Jetty 9.4.24.v20191120: No

--

Boot 2.1.5, Jetty 9.4.15.v20190215: Yes

Boot 2.1.5, Jetty 9.4.16.v20190411: Yes

Boot 2.1.5, Jetty 9.4.17.v20190418: Yes

**Boot 2.1.5, Jetty 9.4.18.v20190429: Yes (this is the default pairing for Spring Boot 2.1.5)**

Boot 2.1.5, Jetty 9.4.19.v20190610: Yes

Boot 2.1.5, Jetty 9.4.24.v20191120: Yes

--

**Boot 2.2.1, Jetty 9.4.22.v20191022: Yes (this is the default pairing for Spring Boot 2.2.1)**

Boot 2.2.1, Jetty 9.4.24.v20191120: Yes

*Conclusion: The specific version of Jetty doesn't matter. The issue occurs consistently with Spring 5.1.6/Boot 2.1.5 and up, regardless of the Jetty version used.*


## Tests with different servers (Jetty, Netty, Tomcat Undertow)

_Does the issue occur?_

**Jetty server: Yes**

Reactor Netty server: No

Tomcat Server: No

Undertow Server: No

Conclusion: The issue is produced only by a combination of Spring 5.1.6/Boot 2.1.5 with the Jetty server. We were
able to make requests for significantly larger amounts of data using, e.g., Reactor Netty server without ever
encountering the error.


# III. Background & Analysis

## Q: Is it a problem with WebClient? A: No

We originally encountered this issue with an API gateway proxy server built on Spring Webflux, that uses the
WebFlux `WebClient` to proxy requests and responses from downstream services. Web apps consuming these APIs would
encounter an issue with large JSON responses returned by the API gateway being cut-off part way through.

Our initial assumption was that the issue has to do with the WebClient client connector used, but this hypothesis was
disproven in testing. *Ultimately, what matters is that the response from the server is large, even if no downstream
WebClient call is made.*

The demo app contains an API `/webclient?path="foo"` which proxies a request to the URI `foo` and returns the result
as an un-parsed String. When curling this API and proxying to a downstream URI that returns a very large response to the demo app, 
the demo app will attempt to return this to the caller. The server will close the connection prematurely and the client receives only a partial response.


### Testing different WebClient client connectors

_Does the issue occur?_

**Reactor-netty client: Yes**

**Jetty-reactive-httpclient: Yes**

Conclusion: The client connector doesn't matter. The issue occurs consistently with either client connector, as long as the server component is Jetty.


## Removing WebClient from the mix...

The demo app also contains an API `/just?size=bar` which simply generates a large static string of `size` number of characters
and returns it in the server response using `Mono.just`. When curling this API with a sufficiently large size the server
will close the connection prematurely and the client receives only a partial response.

Conclusion: The use of WebClient as part of the call stack doesn't matter. All that matters is returning a large response
from the server.


## What is a "sufficiently large" response?

We had a difficult time reproducing this issue at first because what is a "sufficiently large" response to trigger
the premature connection close can vary by several orders of magnitude depending on the operating environment where the
code is running.

We were able to reproduce the issue using this demo app in the following operating environments:

- Running locally in the IDE (IntelliJ)
- Running in a local Docker container in Docker Desktop
- Running in a Docker container in Heroku
- Running in a Docker container in a Mesos cluster in our organization's remote data center

The number of bytes of the response that are necessary to trigger the cutoff *decreases* as you go down this list.

In other words, when running in our Mesos cluster, we are able to consistently reproduce the issue when requesting
a response of 78200 bytes or more. But when running in Heroku it requires up to 10x the number of bytes (as our Mesos cluster),
and when running locally (in local Docker or in IDE) it can require up to 100x the number of bytes to trigger the issue.

Something about our Mesos cluster or our organizational network lowers the threshold significantly, but it is still possible to
trigger the issue in these other environments by making the response size sufficiently large.

One hypothesis is that varying threshold has something to do with the network itself (maybe network latency?) over which the request
is being made, but we have yet been unable to empirically test this.

Incidentally the threshold to trigger the issue in our Mesos cluster is *always exactly* 78200 bytes. 


## What about container resource provisioning?

Given that we know the exact threshold to trigger the issue in a particular operating environment (78200 bytes in Mesos),
we were able to test whether container resource provisioning has any impact on triggering the issue.


### Test container resource sizing

_Does the issue occur?_

**Tested with a provisioned container with 512m native memory and 0.1 cpus, and max heap of 256m: Yes, after 78200 bytes**

**Tested with a provisioned container with 2048m native memory and 2.0 cpus, and max heap of 1024m: Yes, after 72800 bytes**

Conclusion: Container resource sizing does not appear to have any impact on whether or not the issue is triggered or any
affect on what is the triggering threshold.
