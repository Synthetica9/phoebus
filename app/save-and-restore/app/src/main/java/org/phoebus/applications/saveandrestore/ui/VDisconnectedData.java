/*
 * Copyright (C) 2020 European Spallation Source ERIC.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.phoebus.applications.saveandrestore.ui;

import org.epics.vtype.VType;

/**
 *
 * <code>VDisconnectedData</code> represents a {@link VType} for a disconnected PV, where the data type is not known.
 *
 * @author <a href="mailto:jaka.bobnar@cosylab.com">Jaka Bobnar</a>
 *
 */
public final class VDisconnectedData extends VType{

    private static final long serialVersionUID = -2399970529728581034L;

    /** The singleton instance */
    public static final VDisconnectedData INSTANCE = new VDisconnectedData();

    private static final String TO_STRING = "---";
    public static final String DISCONNECTED = "DISCONNECTED";

    private VDisconnectedData() {
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return TO_STRING;
    }


    public String getName(){ return "";}

}
