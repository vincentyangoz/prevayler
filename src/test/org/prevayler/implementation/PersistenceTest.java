// Prevayler, The Free-Software Prevalence Layer
// Copyright 2001-2006 by Klaus Wuestefeld
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.
//
// Prevayler is a trademark of Klaus Wuestefeld.
// See the LICENSE file for license details.

package org.prevayler.implementation;

import org.prevayler.Prevayler;
import org.prevayler.PrevaylerFactory;
import org.prevayler.foundation.FileIOTest;
import org.prevayler.foundation.FileManager;
import org.prevayler.foundation.TurnAbortedError;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class PersistenceTest extends FileIOTest {

    private Prevayler<AppendingSystem> _prevayler;

    private String _prevalenceBase;

    @Override public void tearDown() throws Exception {
        if (_prevayler != null) {
            _prevayler.close();
        }
        super.tearDown();
    }

    public void testPersistence() throws Exception {
        newPrevalenceBase();

        crashRecover(); // There is nothing to recover at first. A new system
        // will be created.
        crashRecover();
        append("a", "a");
        append("b", "ab");
        verify("ab");

        crashRecover();
        verify("ab");

        append("c", "abc");
        append("d", "abcd");
        snapshot();
        snapshot();
        verify("abcd");

        crashRecover();
        snapshot();
        append("e", "abcde");
        snapshot();
        append("f", "abcdef");
        append("g", "abcdefg");
        verify("abcdefg");

        crashRecover();
        append("h", "abcdefgh");
        verify("abcdefgh");

        snapshot();
        _prevayler.close();
        File lastSnapshot = new File(_prevalenceBase, "0000000000000000008.snapshot");
        File lastTransactionLog = new File(_prevalenceBase, "0000000000000000008.journal");
        newPrevalenceBase();
        FileManager.produceDirectory(_prevalenceBase);
        lastSnapshot.renameTo(new File(_prevalenceBase, "0000000000000000008.snapshot")); // Moving
        // the
        // file.
        lastTransactionLog.renameTo(new File(_prevalenceBase, "0000000000000000008.journal"));

        crashRecover();
        append("i", "abcdefghi");
        append("j", "abcdefghij");
        crashRecover();
        append("k", "abcdefghijk");
        append("l", "abcdefghijkl");
        crashRecover();
        append("m", "abcdefghijklm");
        append("n", "abcdefghijklmn");
        crashRecover();
        verify("abcdefghijklmn");
    }

    public void testNondeterminsticError() throws Exception {
        newPrevalenceBase();
        crashRecover(); // There is nothing to recover at first. A new system
        // will be created.

        append("a", "a");
        append("b", "ab");
        verify("ab");

        NondeterministicErrorTransaction.armBomb(1);
        try {
            _prevayler.execute(new NondeterministicErrorTransaction("c"));
            fail();
        } catch (Bomb expected) {
            assertEquals("BOOM!", expected.getMessage());
        }

        try {
            _prevayler.execute(new Appendix("x"));
            fail();
        } catch (ErrorInEarlierTransactionError expected) {
            assertEquals("Prevayler is no longer processing transactions due to an Error thrown from an earlier transaction.", expected.getMessage());
        }

        try {
            _prevayler.execute(new NullQuery());
            fail();
        } catch (ErrorInEarlierTransactionError expected) {
            assertEquals("Prevayler is no longer processing queries due to an Error thrown from an earlier transaction.", expected.getMessage());
        }

        try {
            _prevayler.prevalentSystem();
            fail();
        } catch (ErrorInEarlierTransactionError expected) {
            assertEquals("Prevayler is no longer allowing access to the prevalent system due to an Error thrown from an earlier transaction.", expected.getMessage());
        }

        try {
            _prevayler.takeSnapshot();
            fail();
        } catch (ErrorInEarlierTransactionError expected) {
            assertEquals("Prevayler is no longer allowing snapshots due to an Error thrown from an earlier transaction.", expected.getMessage());
        }

        crashRecover();

        // Note that both the transaction that threw the Error and the
        // subsequent transaction *were* journaled, so they get applied
        // successfully on recovery.
        verify("abcx");
    }

    public void testJournalPanic() throws Exception {
        newPrevalenceBase();

        crashRecover();
        append("a", "a");
        append("b", "ab");

        sneakilyCloseUnderlyingJournalStream();

        try {
            _prevayler.execute(new Appendix("x"));
            fail();
        } catch (TurnAbortedError aborted) {
            assertEquals("All transaction processing is now aborted. An exception was thrown while writing to a journal file.", aborted.getMessage());
            assertNotNull(aborted.getCause());
        }

        try {
            _prevayler.execute(new Appendix("y"));
            fail();
        } catch (TurnAbortedError aborted) {
            assertNull(aborted.getMessage());
            assertNull(aborted.getCause());
        }

        crashRecover();
        verify("ab");
        append("c", "abc");
    }

    private void sneakilyCloseUnderlyingJournalStream() throws Exception {
        FileOutputStream journalStream = (FileOutputStream) Sneaky.get(_prevayler, "_publisher._journal._outputJournal._fileOutputStream");
        journalStream.close();
    }

    private void crashRecover() throws Exception {
        if (_prevayler != null)
            _prevayler.close();

        PrevaylerFactory<AppendingSystem> factory = new PrevaylerFactory<AppendingSystem>();
        factory.configurePrevalentSystem(new AppendingSystem());
        factory.configurePrevalenceDirectory(prevalenceBase());
        factory.configureTransactionFiltering(false);
        _prevayler = factory.create();
    }

    private void snapshot() throws IOException {
        _prevayler.takeSnapshot();
    }

    private void append(String appendix, String expectedResult) throws Exception {
        _prevayler.execute(new Appendix(appendix));
        verify(expectedResult);
    }

    private void verify(String expectedResult) {
        assertEquals(expectedResult, system().value());
    }

    private AppendingSystem system() {
        return _prevayler.prevalentSystem();
    }

    private String prevalenceBase() {
        return _prevalenceBase;
    }

    private void newPrevalenceBase() throws Exception {
        _prevalenceBase = _testDirectory + File.separator + System.currentTimeMillis();
    }

}
