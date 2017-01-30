Akka PostgreSQL
===============

Actually this is a PostgreSQL client built with [akka-stream](http://doc.akka.io/docs/akka/2.4/scala/stream/index.html) and is fully non-blocking, i.e. everything will return a `Future`.
  
The basic implementation is as follow:

    Broadcast Hub ~> dynamic Connection ~> Merge Hub
    
Which means that any client could attach itself to the "output stream" i.e. can log any message that the server will send or can actually create multiple sources to the client.

## TODO:

- [ ] correct Backpressure, currently it will fail with an overflow, however the correct implementation would backpressure/drop upstream (and or throttle)
- [ ] Java API
- [ ] Tests
- [ ] More complete API, i.e. Prepared Statements (and their return), Statements with Return, Transactions
- [ ] Query DSL

## Motivation

The basic idea is to use PostgreSQL as a PubSub implementation. Currently we don't want to add another server like redis to do it, just for PubSub or recreate or HA solution based on redis,
that's why we implemented this.

## Reference Implementation of Parsers:

Reference: https://github.com/mauricio/postgresql-async

## Command Complections

if we issue any command we will always get a `ReadyForQuery` which will indicate that we are now ready to process the next query.