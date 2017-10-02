# [OpenCompany](https://github.com/open-company) Change Service

[![MPL License](http://img.shields.io/badge/license-MPL-blue.svg?style=flat)](https://www.mozilla.org/MPL/2.0/)
[![Build Status](http://img.shields.io/travis/open-company/open-company-change.svg?style=flat)](https://travis-ci.org/open-company/open-company-change)
[![Dependency Status](https://www.versioneye.com/user/projects/592ac555a8a0560033ef3675/badge.svg?style=flat)](https://www.versioneye.com/user/projects/592ac555a8a0560033ef3675)
[![Roadmap on Trello](http://img.shields.io/badge/roadmap-trello-blue.svg?style=flat)](https://trello.com/b/3naVWHgZ/open-company-development)


## Background

> There's no going back, and there's no hiding the information. So let everyone have it.

> -- Andrew Kantor

Companies struggle to keep everyone on the same page. People are hyper-connected in the moment but still don't know what's happening across the company. Employees and investors, co-founders and execs, customers and community, they all want more transparency. The solution is surprisingly simple and effective - great company updates that build transparency and alignment.

With that in mind we designed the [Carrot](https://carrot.io/) software-as-a-service application, powered by the open source [OpenCompany platform](https://github.com/open-company). The product design is based on three principles:

1. It has to be easy or no one will play.
2. The "big picture" should always be visible.
3. Alignment is valuable beyond the team, too.

Carrot simplifies how key business information is shared with stakeholders to create alignment. When information about growth, finances, ownership and challenges is shared transparently, it inspires trust, new ideas and new levels of stakeholder engagement. Carrot makes it easy for founders to engage with employees and investors, creating alignment for everyone.

[Carrot](https://carrot.io/) is GitHub for the rest of your company.

Transparency expectations are changing. Organizations need to change as well if they are going to attract and retain savvy employees and investors. Just as open source changed the way we build software, transparency changes how we build successful companies with information that is open, interactive, and always accessible. Carrot turns transparency into a competitive advantage.

To get started, head to: [Carrot](https://carrot.io/)


## Overview

The OpenCompany Change Service handles tracking the read and unread status of content resources.


## Local Setup

Prospective users of [Carrot](https://carrot.io/) should get started by going to [Carrot.io](https://carrot.io/). The following local setup is **for developers** wanting to work on the OpenCompany Change Service.

Most of the dependencies are internal, meaning [Leiningen](https://github.com/technomancy/leiningen) will handle getting them for you. There are a few exceptions:

* [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) - a Java 8 JRE is needed to run Clojure
* [Amazon Web Services DynamoDB](https://aws.amazon.com/dynamodb/) or [DynamoDB Local](http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html) - fast NoSQL database
* [Leiningen](https://github.com/technomancy/leiningen) 2.5.1+ - Clojure's build and dependency management tool

#### Java

Chances are your system already has Java 8+ installed. You can verify this with:

```console
java -version
```

If you do not have Java 8+ [download it](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and follow the installation instructions.

#### Leiningen

Leiningen is easy to install:

1. Download the latest [lein script from the stable branch](https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein).
1. Place it somewhere that's on your $PATH (`env | grep PATH`). `/usr/local/bin` is a good choice if it is on your PATH.
1. Set it to be executable. `chmod 755 /usr/local/bin/lein`
1. Run it: `lein` This will finish the installation.

Then let Leiningen install the rest of the dependencies:

```console
git clone https://github.com/open-company/open-company-change.git
cd open-company-change
lein deps
```

#### DynamoDB Local

DynamoDB is easy to install with the [executable jar](http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html#DynamoDBLocal.DownloadingAndRunning) for most operating systems.

Extract the compressed file to a place that will be handy to run DynamoDB from, `/usr/local/dynamodb` for example will
work on most Unix-like operating systems.

```console
tar -xvf dynamodb_local_latest.tar.gz -C /usr/local/dynamodb
```

Run DynamoDB on port 8000 with:

```console
cd /usr/local/dynamodb && java -Djava.library.path=DynamoDBLocal_lib -jar DynamoDBLocal.jar -sharedDb
```

##### AWS DynamoDB

For production, it is recommended you use Amazon DynamoDB in the cloud rather than DynamoDB Local. Follow the
[instructions](http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/SettingUp.DynamoWebService.html)
for setting up the cloud service in your AWS account.


## Technical Design

The OpenCompany Change Service handles tracking the read and unread status of content resources. The service does
this at the content container level, not at the individual content item level.

For any given content container, the service knows the time a user last viewed that container, or that the
viewer has never viewed that container. In addition, the service knows the time content in that container was
last updated.

By tracking just this information, the service can fulfill its key responsibility, answering the question: for user X,
is there anything new in content container Y, and if there is new content, the content that is new is the content
that is newer than what time.

The change service is composed of 2 main responsibilities for handling *static* content change notifications, which are
content changes that occur while there's no watcher connected and watching for them:

- Consuming content creation events from the storage service
- Recording the timestamp of the newest event for each container

The change service is composed of 3 main responsibilities for handling *dynamic* content change notifications, which are
content changes that occur while a watcher is connected and watching for them:

- Handling WebSocket connections from watchers that want to get notified of newly created content
- Receiving a user and a list of content containers via the WebSocket connection, and replying with the 
time of the newest content in that container and the last time the user saw that container
- Notifying watchers, via their WebSocket connection, when containers they are watching subsequently get new content

### SQS Messaging

The change service consumes SQS messages in JSON format from the change queue. These messages inform the change service about new
content in a container.

```
{
  "created-at": ISO8601,
  "container-id": 4hex-4hex-4hex UUID,
  "content-id": 4hex-4hex-4hex UUID,  
}
```

### WebSocket Messaging

WebSocket messages are in [EDN format](https://github.com/edn-format/edn).

Client connects to the server at `/change-socket/user/<user-uuid>` with a `:chsk/handshake` message.

Client sends a `:container/watch` message over the socket with a sequence of container UUIDs.

```clojure
["1234-abcd-1234" "bcde-2345-bcde" "3456-a1b2-3456"]
```

Server replies to the `:container/watch` message with a `:container/status` message that has the sequence of
container times from the watch with the timestamps of most recent change for that container and most recent
seen at for that user.

```clojure
[{:container-id "1234-abcd-1234" :change-at ISO8601 :seen-at ISO8601} 
 {:container-id "3456-a1b2-3456" :change-at ISO8601 :seen-at ISO8601}]
```

Note well: 
  * The most common container response in the sequence will have both a `:change-at` and a `:seen-at` property. The change service client can compare those two values, if the `:change-at` property is more recent than the `:seen-at` property, then the container has unseen content. If the `:seen-at` property is more recent than the `:change-at` property, then the container doesn't have unseen content for that user.
  * Some of the containers that were included in the `:container/watch` message may be missing, which indicates there is no record of the user having ever seen the container, or of the container having seen new content. The change service client is to treat this as there being no new content in the container for that user.
  * Some containers in the sequence may be missing just the `:change-at` property which indicates there is no record of the container having seen new content. The change service client is to treat this as there being no new content for the container.
  * Some containers in the sequence may be missing just the `:seen-at` property which indicates there is no record of
the user having seen the container. The change service client is to treat this as there being new content in the container for that user.

At any point, the client may send a `:container/seen` message containing a container UUID and a timestamp to
indicate the user has seen the container.

```clojure
{:container-id "1234-abcd-1234" seen-at: ISO8601}
```

At any point, the server may send a `:container/change`, this indicates new content in a particular container.

```clojure
{:container-id "1234-abcd-1234" :change-at ISO8601}
```

The client can subsequently send additional `:container/watch` messages at any time, typically when the user has created
new containers during the session.


## Testing

Tests are run in continuous integration of the `master` and `mainline` branches on [Travis CI](https://travis-ci.org/open-company/open-company-change):

[![Build Status](https://travis-ci.org/open-company/open-company-change.svg?branch=master)](https://travis-ci.org/open-company/open-company-change)

To run the tests locally:

```console
lein kibit
lean eastwood
```


## Participation

Please note that this project is released with a [Contributor Code of Conduct](https://github.com/open-company/open-company-change/blob/mainline/CODE-OF-CONDUCT.md). By participating in this project you agree to abide by its terms.


## License

Distributed under the [Mozilla Public License v2.0](http://www.mozilla.org/MPL/2.0/).

Copyright © 2017 OpenCompany, LLC.
