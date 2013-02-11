/*
 * Copyright (c) 2013. John May <jwmay@users.sf.net>
 *
 * Contact: cdk-devel@lists.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 * All we ask is that proper credit is given for our work, which includes
 * - but is not limited to - adding the above copyright notice to the beginning
 * of your source code files, and to any copyright notice that you may distribute
 * with programs based on this work.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 U
 */

package org.openscience.cdk.hash.stereo;

import org.openscience.cdk.annotations.TestClass;
import org.openscience.cdk.annotations.TestMethod;
import org.openscience.cdk.hash.stereo.parity.GeometricParity;
import org.openscience.cdk.hash.stereo.parity.PermutationParity;

import java.util.Arrays;

/**
 * Given a geometric parity and a permutation parity encode the parity of the
 * combination at the specified stereo centre indices.
 *
 * @author John May
 * @cdk.module hash
 */
@TestClass("org.openscience.cdk.hash.stereo.GeometryEncoderTest")
public final class GeometryEncoder implements StereoEncoder {

    /* value for a clockwise configuration */
    private static final long CLOCKWISE     = 15543053;

    /* value for a anticlockwise configuration */
    private static final long ANTICLOCKWISE = 15521419;

    /* for calculation the permutation parity */
    private final PermutationParity permutation;

    /* for calculating the geometric parity */
    private final GeometricParity   geometric;

    /* index to encode */
    private final int[]             centres;

    /**
     * Create a new encoder for multiple stereo centres (specified as an
     * array).
     *
     * @param centres     the stereo centres which will be configured
     * @param permutation calculator for permutation parity
     * @param geometric   geometric calculator
     * @throws IllegalArgumentException if the centres[] were empty
     */
    @TestMethod("testConstruction_Empty")
    public GeometryEncoder(int[] centres,
                           PermutationParity permutation,
                           GeometricParity geometric) {
        if (centres.length == 0)
            throw new IllegalArgumentException("no centres[] provided");
        this.permutation = permutation;
        this.geometric   = geometric;
        this.centres     = Arrays.copyOf(centres, centres.length);
    }

    /**
     * Convenience method to create a new encoder for a single stereo centre.
     *
     * @param centre      a stereo centre which will be configured
     * @param permutation calculator for permutation parity
     * @param geometric   geometric calculator
     * @throws IllegalArgumentException if the centres[] were empty
     */
    @TestMethod("testConstruction_Singleton")
    public GeometryEncoder(int centre,
                           PermutationParity permutation,
                           GeometricParity   geometric){
        this(new int[]{centre}, permutation, geometric);
    }

    /**
     * Encodes the {@code centres[]} specified in the constructor as either
     * clockwise/anticlockwise or none. If there is a permutation parity but no
     * geometric parity then we can not encode the configuration and 'true' is
     * returned to indicate the perception is done. If there is no permutation
     * parity this may changed with the next {@code current[]} values and so
     * 'false' is returned.
     *
     * @inheritDoc
     */
    @TestMethod("testEncode_Clockwise,testEncode_Anticlockwise," +
                        "testEncode_Clockwise_Alt,testEncode_Anticlockwise_Alt," +
                        "testEncode_Clockwise_Two, testEncode_Anticlockwise_Two," +
                        "testEncode_NoGeometry,testEncode_NoPermutation")
    @Override public boolean encode(long[] current, long[] next) {

        int p = permutation.parity(current);

        // if is a permutation parity (all neighbors are different)
        if (p != 0) {

            // multiple with the geometric parity
            int q = geometric.parity() * p;

            // configure anticlockwise/clockwise
            if (q > 0) {
                for (int i : centres) {
                    next[i] *= ANTICLOCKWISE;
                }
            } else if (q < 0) {
                for (int i : centres) {
                    next[i] *= CLOCKWISE;
                }
            }

            // 0 parity ignored

            return true;
        }
        return false;
    }

    /**
     * @inheritDoc
     */
    @TestMethod("testReset")
    @Override public void reset() {
        // never inactive
    }
}
