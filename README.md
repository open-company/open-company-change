# [OpenCompany](https://github.com/open-company) Change Service

[![AGPL License](http://img.shields.io/badge/license-AGPL-blue.svg?style=flat)](https://www.gnu.org/licenses/agpl-3.0.en.html)
[![Build Status](http://img.shields.io/travis/open-company/open-company-change.svg?style=flat)](https://travis-ci.org/open-company/open-company-change)
[![Dependencies Status](https://versions.deps.co/open-company/open-company-change/status.svg)](https://versions.deps.co/open-company/open-company-change)


## Background

> There's no going back, and there's no hiding the information. So let everyone have it.

> -- Andrew Kantor

Teams struggle to keep everyone on the same page. People are hyper-connected in the moment with chat and email, but it gets noisy as teams grow, and people miss key information. Everyone needs clear and consistent leadership, and the solution is surprisingly simple and effective - **great leadership updates that build transparency and alignment**.

With that in mind we designed [Carrot](https://carrot.io/), a software-as-a-service application powered by the open source [OpenCompany platform](https://github.com/open-company) and a source-available [web UI](https://github.com/open-company/open-company-web).

With Carrot, important company updates, announcements, stories, and strategic plans create focused, topic-based conversations that keep everyone aligned without interruptions. When information is shared transparently, it inspires trust, new ideas and new levels of stakeholder engagement. Carrot makes it easy for leaders to engage with employees, investors, and customers, creating alignment for everyone.

Transparency expectations are changing. Organizations need to change as well if they are going to attract and retain savvy teams, investors and customers. Just as open source changed the way we build software, transparency changes how we build successful companies with information that is open, interactive, and always accessible. Carrot turns transparency into a competitive advantage.

To get started, head to: [Carrot](https://carrot.io/)


## Overview

The OpenCompany Change Service handles tracking the read and unread status of content resources.


## Local Setup

Prospective users of [Carrot](https://carrot.io/) should get started by going to [Carrot.io](https://carrot.io/). The following local setup is **for developers** wanting to work on the OpenCompany Change Service.

Most of the dependencies are internal, meaning [Leiningen](https://github.com/technomancy/leiningen) will handle getting them for you. There are a few exceptions:

* [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) - a Java 8 JRE is needed to run Clojure
* [Amazon Web Services DynamoDB](https://aws.amazon.com/dynamodb/) or [DynamoDB Local](http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html) - fast NoSQL database
* [Leiningen](https://github.com/technomancy/leiningen) 2.7.1+ - Clojure's build and dependency management tool

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
mkdir /usr/local/dynamodb
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

#### Required Secrets

Make sure you update the section in `project.clj` that looks like this to contain your actual AWS secrets and
SQS queue name:

```clojure
;; Dev environment and dependencies
:dev [:qa {
  :env ^:replace {
    :log-level "debug"
    :aws-access-key-id "CHANGE-ME"
    :aws-secret-access-key "CHANGE-ME"
    :aws-sqs-change-queue "CHANGE-ME"
  }
```

You can also override these settings with environmental variables in the form of `AWS_ACCESS_KEY_ID` and
`AWS_SECRET_ACCESS_KEY`, etc. Use environmental variables to provide production secrets when running in production.

You will also need to subscribe the SQS queue to the storage SNS topic. To do this you will need to go to the AWS console and follow these instruction:

Go to the AWS SQS Console and select the change queue configured above. From the 'Queue Actions' dropdown, select 'Subscribe Queue to SNS Topic'. Select the SNS topic you've configured your Storage Service instance to publish to, and click the 'Subscribe' button.


## Usage

Prospective users of [Carrot](https://carrot.io/) should get started by going to [Carrot.io](https://carrot.io/). The following usage is **for developers** wanting to work on the OpenCompany Change Service.

**Make sure you've updated `project.clj` as described above.**

To start a production instance:

```console
lein start!
```

Or to start a development instance:

```console
lein start
```

To clean all compiled files:

```console
lein clean
```

To create a production build run:

```console
lein build
```

## Technical Design

The OpenCompany Change Service handles tracking the seen and unseen status of content resources. The service does this somewhat indirectly, at the content container level, *not* at the individual content item level.

For any given content container, the service knows the time any specific user last viewed that container, or that the
user has never viewed that container. In addition, the service knows the time that the content in that container was
last updated.

By tracking just this information, the service can fulfill its key responsibility, answering the question: *for
specific user X, is there any never before seen content in specific container Y, and if there is, the content that
hasn't been seen is the content that is newer than time Z.*

The change service is composed of 2 main responsibilities for handling *static* content change notifications, which are
content changes that occur while there's no watcher connected and watching for them:

- Consuming content creation events
- Recording the timestamp of the newest creation event for each container

The change service is composed of 3 main responsibilities for handling *dynamic* content change notifications, which are
content changes that occur while a watcher is connected and watching for them:

- Handling WebSocket connections from watchers that want to get notified of newly created content
- Receiving a user and a list of content containers via the WebSocket connection, and replying with the 
time of the newest content in that container and the last time the user saw that container
- Notifying watchers, via their WebSocket connection, when containers they are watching subsequently get new content

![Change Service Diagram](https://cdn.rawgit.com/open-company/open-company-change/mainline/docs/Change-Service.svg)


### DynamoDB Schema

The DynamoDB schema is quite simple and is made up of 2 tables: `change`, and `seen`. To support multiple environments, these tables are prefixed with an environment name, such as `staging_change` or `production_seen`.

The `change` table has a string partition key called `container_id` and a string sort key called `item_id`. A full item in the table is:

```
{
  "container_id": 4hex-4hex-4hex UUID, _partition key_
  "item_id":  4hex-4hex-4hex UUID, _sort_key_
  "change_at": ISO8601,
  "ttl": epoch-time
}
```

The meaning of each item above is that the container specified by the `container_id` saw a creation event on `item_id` at the `change_at` time, and this record will expire and be removed from DynamoDB at `ttl` time (configured by `change_ttl` in `config.clj`.

The `seen` table has a string partition key called `user_id`, and a string sort key called `container_item_id`. A full item in the table is:

```
{
  "user_id": 4hex-4hex-4hex UUID, _partition key_
  "container_item_id": 4hex-4hex-4hex-4hex-4hex-4hex UUID, _sort key_
  "container_id": 4hex-4hex-4hex UUID,
  "item_id": 4hex-4hex-4hex UUID,
  "seen_at": ISO8601,
  "ttl": epoch-time
}
```

The meaning of each item above is that the user specified by the `user_id` last saw the item specified by the `item_id` in the container specified by the `container_id` at the `change_at` time, and this record will expire and be removed from DynamoDB at `ttl` time (configured by `seen-ttl` in `config.clj`.

Sometimes the `item_id` is a specific value which indicates they saw the entire container.

The `read` table has a string partition key called `item_id`, and a string sort key called `user_id`. A full item in the table is:

```
{
  "item_id": 4hex-4hex-4hex UUID, _partition key_
  "user_id": 4hex-4hex-4hex UUID, _sort key_
  "org_id": 4hex-4hex-4hex UUID,
  "container_id": 4hex-4hex-4hex UUID,
  "name": string,
  "avatar_url": string,
  "read_at": ISO8601,
}
```

The meaning of each item above is that the user specified by the `user_id` with the name specified by `name` and the avatar specified by `avatar_url` last read the item specified by the `item_id` in the container specified by the `container_id` of the org specified by the `org_id` at the `read_at` time.


### SQS Messaging

The change service consumes SQS messages in JSON format from the change queue. These messages inform the change service
about changes. The `user` is the user that made the change. This is used so
consumers of this service can ignore events from specific users (such as from themselves).

```
{
 :notification-type "add|update|delete",
 :notification-at ISO8601,
 :user {...},
 :org {...},
 :board {...},
 :content {:new {...},
           :old {...}}
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

At any point, the server may send a `:container/change`, this indicates new content in a particular container, created by a particular user.

```clojure
{:container-id "1234-abcd-1234" :change-at ISO8601 :user-id "5678-dcba-8765"}
```

The client can subsequently send additional `:container/watch` messages at any time, typically when the user has created
new containers during the session.


## Testing

Tests are run in continuous integration of the `master` and `mainline` branches on [Travis CI](https://travis-ci.org/open-company/open-company-change):

[![Build Status](https://travis-ci.org/open-company/open-company-change.svg?branch=master)](https://travis-ci.org/open-company/open-company-change)

To run the tests locally:

```console
lein kibit
lein eastwood
lein midje
```


## Participation

Please note that this project is released with a [Contributor Code of Conduct](https://github.com/open-company/open-company-change/blob/mainline/CODE-OF-CONDUCT.md). By participating in this project you agree to abide by its terms.


## License

Distributed under the [GNU Affero General Public License Version 3](https://www.gnu.org/licenses/agpl-3.0.en.html).

Copyright © 2017-2018 OpenCompany, LLC.

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the [GNU Affero General Public License](https://www.gnu.org/licenses/agpl-3.0.en.html) for more details.