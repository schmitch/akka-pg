Akka PostgreSQL
===============

This is the planning project of a async full backpresure Akka PostgreSQL.

Actually the Flow will be quite heavy, but the basic idea is that everything is a Sync and a Source.

The connection will be representend by a `Source.queue` and a `Sink.queue`, they will be kept open until the TCP connection is closed/failed. 
Maybe I need to add reconnect facilities later, that can automatically reconnect the connection if it failed (and probably retry failed elements x times). 

On top of that it will actually represent a client sending a sub `Source.queue` over the connection queue, that will be kept open until the user cancel/complete's it.
It basically represent's a transaction and will fail the transaction if the queue will be canceled and the transaction will be completed if the user submit's it.

---

The User actually may also pool the connection so that we add another queue possibility on top of the actual implementation. 
The hard part will be that we need to combine the Sink's at the end, so that the user can actually get notifies over the whole pool.

To get notifies a user needs to actually `Listen` on any connection (of the pool). 
Another possible way would be to implement that would be to actually attach a single connection to the notify which would be harder to retry 



Broadcast Hub ~> dynamic Connection ~> Merge Hub