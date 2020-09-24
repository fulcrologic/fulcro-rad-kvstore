:source-highlighter: coderay
:source-language: clojure
:toc:
:toc-placement: preamble
:sectlinks:
:sectanchors:
:sectnums:

image:https://img.shields.io/clojars/v/com.fulcrologic/fulcro-rad-kvstore.svg[link=https://clojars.org/com.fulcrologic/fulcro-rad-kvstore]

A Fulcro RAD plugin that provides an interface for building database adaptors to support databases that generally store
things as documents with keys.

== Trying it Out

This Fulcro RAD plugin with adaptors is in development and is not yet ready for production.

A recent copy of the https://github.com/fulcrologic/fulcro-rad-demo[RAD Demo] source code is included. So
this project's source code makes up the first three steps here:

 RAD Demo -> Key Value plugin -> ::key-value/key-store -> ::kv-key-store/store -> Redis | Firebase | MongoDB ...

`::kv-key-store/store` is the binding for the adaptor supplied by another library.

Take a look inside `/src/demo-project/config/defaults.edn` just to check that the `:main` Key Value database has
`:key-value/kind` of `:memory` or `:filestore`. We don't want to have to install Redis just yet.

In a terminal type `make cljs`. Shadow CLJS should lead you through the browser/CLJS part of the installation. For the
server side setup a REPL choosing 'Run with Deps' with the alias `dev`. Optionally set 'JVM Args'
to `-Dguardrails.enabled=true`. Bring the REPL up. `(user/refresh)` will reload the namespaces (compilation step)
and `(user/restart)` will get the Demo's dependency graph of Mount components up and running, which includes seeding
the database.

Back to the client. Bring up a browser tab at http://localhost:9630/ and start a watch on the `:main` build. When
compilation is complete refresh the browser at http://localhost:3000/. The Demo Application should now be working.

== Conventions

The following namespace aliases are used in the content of this document:

[source,clojure]
-----
(ns x
  (:require
     [com.fulcrologic.rad.database-adapters.key-value :as key-value]
     [com.fulcrologic.rad.database-adapters.key-value.key-store :as kv-key-store]
     [com.fulcrologic.rad.database-adapters.key-value.write :as kv-write]
     [com.fulcrologic.rad.database-adapters.key-value.pathom :as kv-pathom]
     ;; Don't need an alias to load a ns so the multimethod's defmethod can be found
     [com.fulcrologic.rad.database-adapters.key-value.redis]
     [com.fulcrologic.rad.database-adapters.datomic :as datomic]
     [com.example.components.config :as config]
     [com.example.components.delete-middleware :as delete]
     [com.example.components.save-middleware :as save]
     [com.example.components.parser :as parser]
     [com.fulcrologic.rad.pathom :as pathom]
     [immutant.web :as web]
     ))
-----

The RAD Demo is at `com.example`.

== Introduction

This library allows Fulcro RAD to read and write to a https://github.com/replikativ/konserve[Konserve]-supported
key value database. Thus supporting a new database may mean a trivial 'include it' fix here, or contributing to
Konserve in the first instance. For example at time of writing MongoDB was not supported by Konserve.

.Entities are always stored with:
- key being a stricter version of `eql/ident?` (specifically table has to be a `/id` keyword and the second part a `uuid?`)
- value being a map of attributes (an entity!), where the attribute values are either scalars or references. A
reference can be either an ident or a vector of idents.

== Creation

To create a `::key-value/key-store` call `key-value/start` passing in the RAD configuration map.
`::kv-key-store/store` inside the returned map is where the real Konserve
adaptor is located. Unless the `:kind` of adaptor you are creating is `:memory`, `:filestore` or `:indexeddb`
make sure you have required the namespace associated with the adaptor you are requesting, so that the defmethod of the
`make-adaptor` multimethod can be found. As mentioned in a comment in the example `:require`, there is no
need for an alias.

 {::kv-key-store/keys [store]} (key-value/start config/config)

== Functions

`::key-value/key-store` can be destructured:

  {::kv-key-store/keys [store table->rows table->ident-rows ident->entity write-entity ids->entities]}

The easiest way to get started is to use these functions directly. All bar one of them are for retrieving entities.
For example `ident->entity` returns what is stored at an ident, and `table->rows` returns all the data of a table.
Here is how you would get all the active accounts (from the Demo project):

  (->> (table->rows :account/id)
       (filter :account/active?)
       (mapv #(select-keys % [:account/id])))

Here's one way you could do the same thing using Konserve's store directly:

    (<!!
      (go
        (->> (vals (<! (k/get-in store [:account/id])))
             (filter :account/active?)
             (mapv #(select-keys % [:account/id])))))

Here both are equally 'async', but when there are many fetches you are going to get better performance using
`::kv-key-store/store` directly.

Additionally there are some useful functions in the `::kv-write` namespace: `import`,
`remove-table-rows` and `write-tree`. And in the `::key-value` namespace there is `export`.

== RAD Integration

.As with all RAD Database plugins:
- It comes with delete and save middleware so you don't have to write your own Pathom mutations.
- Is capable of generating the 'id -> entity' Pathom resolvers.
- Injects values into the Pathom `env` that are then picked up by these resolvers.
- Has a function to start the plugin that picks up values set in the RAD configuration.

.Entry points from RAD Demo into Key Value plugin
|===
|Description |Demo Mount component |Function call

|Generation of Pathom resolvers
|`com.example.components.auto-resolvers/automatic-resolvers`
|`kv-pathom/generate-resolvers`

|Save Middleware
|`::save/middleware`
|`kv-pathom/wrap-save`

|Delete Middleware
|`::delete/middleware`
|`kv-pathom/wrap-delete`

|Injection into Pathom's `env`
|`::parser/parser`
|`kv-pathom/pathom-plugin`

|Create connection
|`::config/config`,`com.example.components.seeded-connection/kv-connections`
|`key-value/start`
|===

An area of integration not included on this table is where your hand written resolvers need to query the
database. Here queries will receive `env`, which they can then pass to the function
`kv-pathom/env->key-store` to get the `::key-value/key-store`. With this key store
in hand either use one of the pre-written functions the map contains, or use Konserve directly.

 {::kv-key-store/keys [store]} (kv-pathom/env->key-store env)

== Comparison with Datomic Database plugin

Compared to the Datomic plugin some things have been left undone.

Even although the Pathom `env` of this plugin has `::key-value/connections` and `::key-value/databases` only
one connection/database is ever used. (With this type of database there is no difference between a connection and a
database). So with current functionality we could get away with just having `::key-value/database`.
The Datomic plugin requires this setup to support sharding, which has been left undone for the Key Value plugin.
Note that even if we invented a new key such as `::key-value/database` to go into the `env`, we could still keep
`::key-value/databases` in the config file. You'll probably never need to worry about all this however,
as the function `kv-pathom/env->key-store` abstracts it away, and you'll usually be editing `.edn` config
files rather than creating them from scratch.

There is no automatic schema generation. Unlike Datomic, Key Value databases do not have schemas to generate.

This plugin currently eschews looking to RAD attributes to ascertain the primary key of entities, instead making
the assumption that your entities are strict (according to the namespace `::strict-entity`). Thus if you do not need
automatic Pathom resolver generation then this plugin can be used outside of RAD. Of course you can also always use
Konserve directly!

The last significant thing this plugin lacks is the useful function
`datomic/empty-db-connection` that gives a data-less database - good for making tests that build up
just the data they need, not touching existing databases. The closest we have is
`kv-key-store/import` which requires an existing database and can be used to destroy the existing data (so not
actually importing anything).

== Redis Installation

These instructions worked well for me (on a Linux machine):
https://www.digitalocean.com/community/tutorials/how-to-install-and-secure-redis-on-ubuntu-18-04

== Updating Demo project

This section more for maintainers, just outlining steps to be taken when copying in the latest RAD Demo.

Apart from `com.example.components` and `config`, overwrite all with new files. So `com.example`: `client.cljs`,
`model.cljc` and `ui.cljc`, then `com.example.model` and `com.example.ui`. The mount components should not change but
you might want to check the `.edn` config files.

The Demo project should now work; the following changes are optional.

Additionally I've found I need to add this to every Form, regardless of the Database plugin:

 ::form/confirm (fn [message]
                  #?(:cljs (js/confirm message)))

`time-zone` is Datomic-specific so remove it by commenting out timezone/attributes from com.example.model and on
whatever UIs TZ appears - in fo/attributes in AccountForm for example (`timezone/zone-id`).

== Deployment

There will be a base Maven artifact and one for each substantial adaptor. Thus `:memory`, `:filestore` and
`:indexeddb` will be covered by the base artifact.

Please see the Makefile to produce your own local jars.

== Copyright and License

Copyright (c) 2017-2019, Fulcrologic, LLC
The MIT License (MIT)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
