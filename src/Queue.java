/*
 * J2ME_MPEG: MPEG-1 decoder for J2ME
 *
 * Copyright (c) 2009 David Caabeiro
 *
 */

import java.util.Vector;

public class Queue {
    private Vector queue = new Vector();

    public void put(Object obj) {
        synchronized (queue) {
            queue.addElement(obj);
            queue.notify();
        }
    }

    public Object get() {
        synchronized (queue) {
            while (queue.isEmpty()) {
                try {
                    queue.wait();
                }
                catch (InterruptedException ignore) {}
            }

            Object obj = queue.firstElement();
            queue.removeElementAt(0);
            return obj;
        }
    }
}
