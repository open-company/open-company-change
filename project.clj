(defproject open-company-change "0.1.0-SNAPSHOT"
  :description "OpenCompany Change Service"
  :url "https://github.com/open-company/open-company-change"
  :license {
    :name "GNU Affero General Public License Version 3"
    :url "https://www.gnu.org/licenses/agpl-3.0.en.html"
  }

  :min-lein-version "2.9.1"

  ;; JVM memory
  :jvm-opts ^:replace ["-Xms512m" "-Xmx3072m" "-server"]

  ;; All profile dependencies
  :dependencies [
    ;; Lisp on the JVM http://clojure.org/documentation
    [org.clojure/clojure "1.10.1"]
    ;; Command-line parsing https://github.com/clojure/tools.cli
    [org.clojure/tools.cli "1.0.194"]
    ;; Web application library https://github.com/ring-clojure/ring
    [ring/ring-devel "1.8.0"]
    ;; Web application library https://github.com/ring-clojure/ring
    ;; NB: clj-time pulled in by oc.lib
    ;; NB: joda-time pulled in by oc.lib via clj-time
    ;; NB: commons-codec pulled in by oc.lib
    [ring/ring-core "1.8.0" :exclusions [clj-time joda-time commons-codec]]
    ;; CORS library https://github.com/jumblerg/ring.middleware.cors
    [jumblerg/ring.middleware.cors "1.0.1"]
    ;; Ring logging https://github.com/nberger/ring-logger-timbre
    ;; NB: com.taoensso/encore pulled in by oc.lib
    ;; NB: com.taoensso/timbre pulled in by oc.lib
    [ring-logger-timbre "0.7.6" :exclusions [com.taoensso/encore com.taoensso/timbre]]
    ;; Web routing https://github.com/weavejester/compojure
    [compojure "1.6.1"]
    ;; DynamoDB client https://github.com/ptaoussanis/faraday
    ;; NB: com.amazonaws/aws-java-sdk-dynamodb is pulled in by amazonica
    ;; NB: joda-time is pulled in by clj-time
    ;; NB: encore pulled in from oc.lib
    [com.taoensso/faraday "1.11.0-alpha1" :exclusions [com.amazonaws/aws-java-sdk-dynamodb joda-time com.taoensso/encore]]
    ;; Faraday dependency, not pulled in? https://hc.apache.org/
    [org.apache.httpcomponents/httpclient "4.5.11"]

    ;; Library for OC projects https://github.com/open-company/open-company-lib
    ;; ************************************************************************
    ;; ****************** NB: don't go under 0.17.29-alpha59 ******************
    ;; ***************** (JWT schema changes, more info here: *****************
    ;; ******* https://github.com/open-company/open-company-lib/pull/82) ******
    ;; ************************************************************************
    [open-company/lib "0.19.0-alpha5" :exclusions [ring/ring-core commons-codec org.clojure/tools.reader]]
    ;; ************************************************************************
    ;; In addition to common functions, brings in the following common dependencies used by this project:
    ;; httpkit - Web server http://http-kit.org/
    ;; core.async - Async programming and communication https://github.com/clojure/core.async
    ;; defun - Erlang-esque pattern matching for Clojure functions https://github.com/killme2008/defun
    ;; if-let - More than one binding for if/when macros https://github.com/LockedOn/if-let
    ;; Component - Component Lifecycle https://github.com/stuartsierra/component
    ;; Schema - Data validation https://github.com/Prismatic/schema
    ;; Timbre - Pure Clojure/Script logging library https://github.com/ptaoussanis/timbre
    ;; Amazonica - A comprehensive Clojure client for the AWS API https://github.com/mcohen01/amazonica
    ;; Sentry - Interface to Sentry error reporting https://github.com/getsentry/sentry-clj
    ;; Cheshire - JSON encoding / decoding https://github.com/dakrone/cheshire
    ;; clj-time - Date and time lib https://github.com/clj-time/clj-time
    ;; Environ - Get environment settings from different sources https://github.com/weavejester/environ
    ;; Sente - WebSocket server https://github.com/ptaoussanis/sente
  ]

  ;; All profile plugins
  :plugins [
    ;; Common ring tasks https://github.com/weavejester/lein-ring
    [lein-ring "0.12.5"]
    ;; Get environment settings from different sources https://github.com/weavejester/environ
    [lein-environ "1.1.0"]
  ]

  :profiles {

    ;; QA environment and dependencies
    :qa {
      :env {
        :hot-reload "false"
      }
      :dependencies [
        ;; Example-based testing https://github.com/marick/Midje
        ;; NB: org.clojure/tools.macro is pulled in manually
        ;; NB: clj-time is pulled in by oc.lib
        ;; NB: joda-time is pulled in by oc.lib via clj-time
        ;; NB: commons-codec pulled in by oc.lib
        [midje "1.9.9" :exclusions [joda-time org.clojure/tools.macro clj-time commons-codec]]
        ;; Clojure WebSocket client https://github.com/cch1/http.async.client
        [http.async.client "1.3.1"]
        ;; Test Ring requests https://github.com/weavejester/ring-mock
        [ring-mock "0.1.5"]
      ]
      :plugins [
        ;; Example-based testing https://github.com/marick/lein-midje
        [lein-midje "3.2.2"]
        ;; Linter https://github.com/jonase/eastwood
        [jonase/eastwood "0.3.7"]
        ;; Static code search for non-idiomatic code https://github.com/jonase/kibit
        [lein-kibit "0.1.8" :exclusions [org.clojure/clojure]]
      ]
    }

    ;; Dev environment and dependencies
    :dev [:qa {
      :env ^:replace {
        :open-company-auth-passphrase "this_is_a_dev_secret" ; JWT secret
        :oc-ws-ensure-origin "true" ; local
        :log-level "debug"
        :aws-access-key-id "CHANGE-ME"
        :aws-secret-access-key "CHANGE-ME"
        :aws-sqs-change-queue "CHANGE-ME"
      }
      :plugins [
        ;; Check for code smells https://github.com/dakrone/lein-bikeshed
        ;; NB: org.clojure/tools.cli is pulled in by lein-kibit
        [lein-bikeshed "0.5.2" :exclusions [org.clojure/tools.cli]]
        ;; Runs bikeshed, kibit and eastwood https://github.com/itang/lein-checkall
        [lein-checkall "0.1.1"]
        ;; pretty-print the lein project map https://github.com/technomancy/leiningen/tree/master/lein-pprint
        [lein-pprint "1.3.2"]
        ;; Check for outdated dependencies https://github.com/xsc/lein-ancient
        [lein-ancient "0.6.15"]
        ;; Catch spelling mistakes in docs and docstrings https://github.com/cldwalker/lein-spell
        [lein-spell "0.1.0"]
        ;; Dead code finder https://github.com/venantius/yagni
        [venantius/yagni "0.1.7" :exclusions [org.clojure/clojure]]
      ]
    }]
    :repl-config [:dev {
      :dependencies [
        ;; Network REPL https://github.com/clojure/tools.nrepl
        [org.clojure/tools.nrepl "0.2.13"]
        ;; Pretty printing in the REPL (aprint ...) https://github.com/razum2um/aprint
        [aprint "0.1.3"]
      ]
      ;; REPL injections
      :injections [
        (require '[aprint.core :refer (aprint ap)]
                 '[clojure.stacktrace :refer (print-stack-trace)]
                 '[clj-time.core :as t]
                 '[clj-time.format :as f]
                 '[clojure.string :as s]
                 '[cheshire.core :as json]
                 '[taoensso.faraday :as far]
                 '[oc.lib.jwt :as jwt]
                 '[oc.lib.db.common :as db-common]
                 '[oc.change.app :refer (app)]
                 '[oc.change.config :as config]
                 '[oc.change.resources.seen :as seen]
                 '[oc.change.resources.change :as change]
                 '[oc.change.resources.read :as read]
                 '[oc.change.resources.follow :as follow]
                 )
      ]
    }]

    ;; Production environment
    :prod {
      :env {
        :env "production"
      }
    }
  }

  :repl-options {
    :welcome (println (str "\n" (slurp (clojure.java.io/resource "ascii_art.txt")) "\n"
                      "OpenCompany Change REPL\n"
                      "\nReady to do your bidding... I suggest (go) or (go <port>) as your first command.\n"))
    :init-ns dev
  }

  :aliases {
    "build" ["do" "clean," "deps," "compile"] ; clean and build code
    "create-migration" ["run" "-m" "oc.change.db.migrations" "create"] ; create a data migration
    "migrate-db" ["run" "-m" "oc.change.db.migrations" "migrate"] ; run pending data migrations
    "start*" ["do" "migrate-db," "run"] ; start the service
    "start" ["with-profile" "dev" "do" "start*"] ; start a development server
    "start!" ["with-profile" "prod" "do" "start*"] ; start a server in production
    "autotest" ["with-profile" "qa" "do" "migrate-db," "midje" ":autotest"] ; watch for code changes and run affected tests
    "test!" ["with-profile" "qa" "do" "clean," "build," "migrate-db," "midje"] ; run all tests
    "repl" ["with-profile" "+repl-config" "repl"]
    "spell!" ["spell" "-n"] ; check spelling in docs and docstrings
    "bikeshed!" ["bikeshed" "-v" "-m" "120"] ; code check with max line length warning of 120 characters
    "ancient" ["ancient" ":all" ":allow-qualified"] ; check for out of date dependencies
  }

  ;; ----- Code check configuration -----

  :eastwood {
    ;; Disable some linters that are enabled by default:
    ;; constant-test - just seems mostly ill-advised, logical constants are useful in something like a `->cond`
    ;; suspcious-experession - unfortunate, but it's failing on defrecord of a com.stuartsierra.component component
    ;; implicit-dependencies - uhh, just seems dumb
    :exclude-linters [:constant-test :suspicious-expression :implicit-dependencies]

    ;; Enable some linters that are disabled by default
    :add-linters [:unused-namespaces :unused-private-vars] ; :unused-locals

    ;; Exclude testing namespaces
    :tests-paths ["test"]

    ;; Exclude utility namespace since it's not used directly from the service app
    :exclude-namespaces [:test-paths oc.change.util.fix-ttl]
  }

  :zprint {:old? false}

  ;; ----- API -----

  :ring {
    :handler oc.change.app/app
    :reload-paths ["src"] ; work around issue https://github.com/weavejester/lein-ring/issues/68
  }

  :main oc.change.app
)
