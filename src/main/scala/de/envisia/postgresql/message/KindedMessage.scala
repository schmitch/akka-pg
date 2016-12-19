/*
 * Copyright (C) 2016. envisia GmbH
 * All Rights Reserved.
 */
package de.envisia.postgresql.message

private [postgresql] trait KindedMessage extends Serializable {
  def kind : Int
}
