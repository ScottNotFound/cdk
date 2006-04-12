/* $RCSfile$
 * $Author: egonw $    
 * $Date: 2006-03-29 10:27:08 +0200 (Wed, 29 Mar 2006) $    
 * $Revision: 5855 $
 * 
 * Copyright (C) 1997-2006  The Chemistry Development Kit (CDK) project
 * 
 * Contact: cdk-devel@lists.sourceforge.net
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA. 
 * 
 */
package org.openscience.cdk.nonotify;

import org.openscience.cdk.Element;
import org.openscience.cdk.interfaces.IChemObjectBuilder;

/**
 * @cdk.module nonotify
 */
public class NNElement extends Element {

    /**
	 * 
	 */
	private static final long serialVersionUID = -6527988212165797651L;

	public NNElement() {
        super();
        setNotification(false);
    }

    public NNElement(String symbol) {
        this();
        this.symbol = symbol;
    }

    public NNElement(String symbol, int atomicNumber) {
        this(symbol);
        this.atomicNumber = atomicNumber;
    }

	public IChemObjectBuilder getBuilder() {
		return NoNotificationChemObjectBuilder.getInstance();
	}
}
