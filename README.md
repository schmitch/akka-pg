Akka PostgreSQL
===============

Actually this is a PostgreSQL client built with [akka-stream](http://doc.akka.io/docs/akka/2.4/scala/stream/index.html) and is fully non-blocking, i.e. everything will return a `Future`.
  
The basic implementation is as follow:

    Broadcast Hub ~> dynamic Connection ~> Merge Hub
    
Which means that any client could attach itself to the "output stream" i.e. can log any message that the server will send or can actually create multiple sources to the client.

## TODO:

- [ ] Java API
- [ ] Tests
- [ ] More complete API, i.e. Prepared Statements (and their return), Statements with Return, Transactions
- [ ] Query DSL

## Reference Implementation of Parsers:

Reference: https://github.com/mauricio/postgresql-async