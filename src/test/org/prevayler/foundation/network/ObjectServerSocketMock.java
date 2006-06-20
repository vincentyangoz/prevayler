// Prevayler, The Free-Software Prevalence Layer
// Copyright 2001-2006 by Klaus Wuestefeld
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.
//
// Prevayler is a trademark of Klaus Wuestefeld.
// See the LICENSE file for license details.

package org.prevayler.foundation.network;

import org.prevayler.foundation.Cool;

import java.io.IOException;

public class ObjectServerSocketMock implements ObjectServerSocket {

    private ObjectSocket _clientSide;

    private final Permit _permit;

    public ObjectServerSocketMock(Permit permit) {
        _permit = permit;
        _permit.addObjectToNotify(this);
    }

    public synchronized ObjectSocket accept() throws IOException {
        _permit.check();

        if (_clientSide != null)
            throw new IOException("Port already in use.");
        ObjectSocketMock result = new ObjectSocketMock(_permit);
        _clientSide = result.counterpart();

        notifyAll(); // Notifies all client threads.
        Cool.wait(this);

        _permit.check();
        return result;
    }

    synchronized ObjectSocket openClientSocket() throws IOException {
        _permit.check();
        while (_clientSide == null)
            Cool.wait(this);
        _permit.check();

        ObjectSocket result = _clientSide;
        _clientSide = null;
        notifyAll(); // Notifies the server thread (necessary) and eventual
        // client threads (harmless).
        return result;
    }

    public void close() {
    }

}
