/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.context;

import java.util.Collections;
import java.util.Set;

/**
 * A context that contains information pertaining to a given invocation.  These contexts typically have the lifespan of
 * a single invocation.
 *
 * @author Manik Surtani (<a href="mailto:manik@jboss.org">manik@jboss.org</a>)
 * @author Mircea.Markus@jboss.com
 * @since 4.0
 */
public interface InvocationContext extends EntryLookup, FlagContainer, Cloneable {

   /**
    * Returns true if the call was originated locally, false if it is the result of a remote rpc.
    */
   boolean isOriginLocal();

   /**
    * Returns true if this call is performed in the context of an transaction, false otherwise.
    */
   boolean isInTxScope();

   /**
    * Returns the in behalf of which locks will be aquired.
    */
   Object getLockOwner();

   /**
    * Returns true if the context has any locked entries associated with it.
    */
   boolean hasLockedEntries();

   boolean isUseFutureReturnType();

   void setUseFutureReturnType(boolean useFutureReturnType);

   InvocationContext clone();

}
