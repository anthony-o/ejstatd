/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.github.anthony_o.ejstatd;

import sun.jvmstat.monitor.MonitorException;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.VmIdentifier;
import sun.jvmstat.monitor.event.HostEvent;
import sun.jvmstat.monitor.event.HostListener;
import sun.jvmstat.monitor.event.VmStatusChangeEvent;
import sun.jvmstat.monitor.BufferedMonitoredVm;
import sun.jvmstat.monitor.remote.RemoteHost;
import sun.jvmstat.monitor.remote.RemoteVm;

import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Set;

/**
 * Concrete implementation of the RemoteHost interface for the HotSpot
 * PerfData <em>rmi:</em> protocol.
 * <p>
 * This class provides remote access to the instrumentation exported
 * by HotSpot Java Virtual Machines through the PerfData shared memory
 * interface.
 *
 * @author Brian Doherty
 * @since 1.5
 */
public class RemoteHostImpl implements RemoteHost, HostListener {

    private MonitoredHost monitoredHost;
    private final Set<Integer> activeVms;

    public RemoteHostImpl() throws MonitorException {
        try {
            this.monitoredHost = MonitoredHost.getMonitoredHost("localhost");
        } catch (URISyntaxException e) { }

        this.activeVms = this.monitoredHost.activeVms();
        this.monitoredHost.addHostListener(this);
    }

    public RemoteVm attachVm(final int lvmid, final String mode) throws RemoteException, MonitorException {
        RemoteVm stub;

        final StringBuilder sb = new StringBuilder();
        sb.append("local://").append(lvmid).append("@localhost");
        if (mode != null) {
            sb.append("?mode=").append(mode);
        }
        final String vmidStr = sb.toString();

        try {
            final VmIdentifier vmid = new VmIdentifier(vmidStr);
            final MonitoredVm mvm = this.monitoredHost.getMonitoredVm(vmid);
            final RemoteVmImpl rvm = new RemoteVmImpl((BufferedMonitoredVm)mvm);
            stub = (RemoteVm) UnicastRemoteObject.exportObject(rvm, Integer.parseInt(System.getProperty("ejstatd.remoteVm.port", "0")));
        }
        catch (final URISyntaxException e) {
            throw new RuntimeException("Malformed VmIdentifier URI: " + vmidStr, e);
        }
        return stub;
    }

    public void detachVm(final RemoteVm rvm) throws RemoteException {
        rvm.detach();
    }

    public int[] activeVms() throws MonitorException {
        Object[] vms = this.monitoredHost.activeVms().toArray();
        int[] vmids = new int[vms.length];

        for (int i = 0; i < vmids.length; i++) {
            vmids[i] = (Integer) vms[i];
        }
        return vmids;
    }

    public void vmStatusChanged(final VmStatusChangeEvent ev) {
        synchronized(this.activeVms) {
            this.activeVms.retainAll(ev.getActive());
        }
    }

    public void disconnected(final HostEvent ev) {
        // we only monitor the local host, so this event shouldn't occur.
    }
}
