/* $RCSfile$
 * $Author$
 * $Date$
 * $Revision$
 *
 * Copyright (C) 1997-2006  The Chemistry Development Kit (CDK) project
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
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *  */
package org.openscience.cdk.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.vecmath.Point3d;

import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.config.AtomTypeFactory;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.graph.rebond.RebondTool;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomType;
import org.openscience.cdk.interfaces.IBioPolymer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemFile;
import org.openscience.cdk.interfaces.IChemModel;
import org.openscience.cdk.interfaces.IChemObject;
import org.openscience.cdk.interfaces.IChemSequence;
import org.openscience.cdk.interfaces.IMonomer;
import org.openscience.cdk.interfaces.ISetOfMolecules;
import org.openscience.cdk.interfaces.IStrand;
import org.openscience.cdk.io.formats.IResourceFormat;
import org.openscience.cdk.io.formats.PDBFormat;
import org.openscience.cdk.io.setting.BooleanIOSetting;
import org.openscience.cdk.io.setting.IOSetting;
import org.openscience.cdk.protein.data.PDBAtom;
import org.openscience.cdk.protein.data.PDBMonomer;
import org.openscience.cdk.protein.data.PDBPolymer;
import org.openscience.cdk.protein.data.PDBStrand;
import org.openscience.cdk.protein.data.PDBStructure;
import org.openscience.cdk.templates.AminoAcids;
import org.openscience.cdk.tools.LoggingTool;
import org.openscience.cdk.tools.manipulator.AtomTypeManipulator;

/**
 * Reads the contents of a PDBFile.
 *
 * <p>A description can be found at <a href="http://www.rcsb.org/pdb/static.do?p=file_formats/pdb/index.html">
 * http://www.rcsb.org/pdb/static.do?p=file_formats/pdb/index.html</a>.
 *
 * @cdk.module  pdb
 *
 * @author      Edgar Luttmann
 * @author      Bradley Smith <bradley@baysmith.com>
 * @author      Martin Eklund <martin.eklund@farmbio.uu.se>
 * @cdk.created 2001-08-06
 *
 * @cdk.keyword file format, PDB
 */
public class PDBReader extends DefaultChemObjectReader {
	
	private LoggingTool logger;
	private BufferedReader _oInput; // The internal used BufferedReader
	private BooleanIOSetting deduceBonding;
	private BooleanIOSetting useRebondTool;
	private BooleanIOSetting readConnect;
	
	private Map atomNumberMap;
	
	/**
	 *
	 * Contructs a new PDBReader that can read Molecules from a given
	 * InputStream.
	 *
	 * @param oIn  The InputStream to read from
	 *
	 */
	public PDBReader(InputStream oIn) {
		this(new InputStreamReader(oIn));
	}
	
	/**
	 *
	 * Contructs a new PDBReader that can read Molecules from a given
	 * Reader.
	 *
	 * @param oIn  The Reader to read from
	 *
	 */
	public PDBReader(Reader oIn) {
		logger = new LoggingTool(this.getClass());
		_oInput = new BufferedReader(oIn);
		initIOSettings();
	}
	
	public PDBReader() {
		this(new StringReader(""));
	}
	
	public IResourceFormat getFormat() {
		return new PDBFormat();
	}
	
	public void setReader(Reader input) throws CDKException {
		if (input instanceof BufferedReader) {
			this._oInput = (BufferedReader)input;
		} else {
			this._oInput = new BufferedReader(input);
		}
	}
	
	public void setReader(InputStream input) throws CDKException {
		setReader(new InputStreamReader(input));
	}
	
	public boolean accepts(Class classObject) {
		Class[] interfaces = classObject.getInterfaces();
		for (int i=0; i<interfaces.length; i++) {
			if (IChemFile.class.equals(interfaces[i])) return true;
		}
		return false;
	}

	/**
	 *
	 * Takes an object which subclasses IChemObject, e.g. Molecule, and will
	 * read this (from file, database, internet etc). If the specific
	 * implementation does not support a specific IChemObject it will throw
	 * an Exception.
	 *
	 * @param oObj  The object that subclasses IChemObject
	 * @return      The IChemObject read  
	 * @exception   CDKException  
	 *
	 */
	public IChemObject read(IChemObject oObj) throws CDKException {
		if (oObj instanceof IChemFile) {
			return (IChemObject)readChemFile((IChemFile)oObj);
		} else {
			throw new CDKException("Only supported is reading of ChemFile objects.");
		}
	}
	
	/**
	 * Read a <code>ChemFile</code> from a file in PDB format. The molecules
	 * in the file are stored as <code>BioPolymer</code>s in the
	 * <code>ChemFile</code>. The residues are the monomers of the
	 * <code>BioPolymer</code>, and their names are the concatenation of the
	 * residue, chain id, and the sequence number. Separate chains (denoted by
	 * TER records) are stored as separate <code>BioPolymer</code> molecules.
	 *
	 * <p>Connectivity information is not currently read.
	 *
	 * @return The ChemFile that was read from the PDB file.
	 */
	private IChemFile readChemFile(IChemFile oFile) 	{
		// initialize all containers
		IChemSequence oSeq = oFile.getBuilder().newChemSequence();
		IChemModel oModel = oFile.getBuilder().newChemModel();
		ISetOfMolecules oSet = oFile.getBuilder().newSetOfMolecules();
		
		// some variables needed
		String cCol;
		PDBAtom oAtom;
		PDBPolymer oBP = new PDBPolymer();
		StringBuffer cResidue;
		String oObj;
		IMonomer oMonomer;
		String cRead = "";
		char chain = 'A';	// To ensure stringent name giving of monomers
		IStrand oStrand;
		int lineLength = 0;
		
		atomNumberMap = new Hashtable();
		
		// do the reading of the Input		
		try {
			do {
				cRead = _oInput.readLine();
				logger.debug("Read line: ", cRead);
				if (cRead != null) {	
					lineLength = cRead.length();
					
					if (lineLength < 80) {
						logger.warn("Line is not of the expected length 80!");
					}
					
					// make sure the record name is 6 characters long
					if (lineLength < 6) {
						cRead = cRead + "      ";
					}
					// check the first column to decide what to do
					cCol = cRead.substring(0,6).toUpperCase();
					if (cCol.equals("ATOM  ")) {
						// read an atom record
						oAtom = readAtom(cRead);
						
						// construct a string describing the residue
						cResidue = new StringBuffer(8);
						oObj = oAtom.getResName();
						if (oObj != null) {
							cResidue = cResidue.append(oObj.trim());
						}
						oObj = oAtom.getChainID();
						if (oObj != null) {
							// cResidue = cResidue.append(((String)oObj).trim());
							cResidue = cResidue.append(String.valueOf(chain));
						}
						oObj = oAtom.getResSeq();
						if (oObj != null) {
							cResidue = cResidue.append(oObj.trim());
						}
						
						// search for an existing strand or create a new one.
						oStrand = oBP.getStrand(String.valueOf(chain));
						if (oStrand == null) {
							oStrand = new PDBStrand();
							oStrand.setStrandName(String.valueOf(chain));
						}
						
						// search for an existing monomer or create a new one.
						oMonomer = oBP.getMonomer(cResidue.toString(), String.valueOf(chain));
						if (oMonomer == null) {
							PDBMonomer monomer = new PDBMonomer();
							monomer.setMonomerName(cResidue.toString());
							monomer.setMonomerType(oAtom.getResName());
							monomer.setChainID(oAtom.getChainID());
							monomer.setICode(oAtom.getICode());
							oMonomer = monomer;
						}
						
						// add the atom
						oBP.addAtom(oAtom, oMonomer, oStrand);
						if (readConnect.isSet() && atomNumberMap.put(new Integer(oAtom.getSerial()), oAtom) != null) {
							logger.warn("Duplicate serial ID found for atom: ", oAtom);
						}
						logger.debug("Added ATOM: ", oAtom);
						
						/** As HETATMs cannot be considered to either belong to a certain monomer or strand,
						 * they are dealt with seperately.*/
					} else if(cCol.equals("HETATM"))	{
						// read an atom record
						oAtom = readAtom(cRead);
						oBP.addAtom(oAtom);
						if (atomNumberMap.put(new Integer(oAtom.getSerial()), oAtom) != null) {
							logger.warn("Duplicate serial ID found for atom: ", oAtom);
						}
						logger.debug("Added HETATM: ", oAtom);
					} else if (cCol.equals("TER   ")) {
						// start new strand						
						chain++;
						oStrand = new PDBStrand();
						oStrand.setStrandName(String.valueOf(chain));
						logger.debug("Added new STRAND");
					} else if (cCol.equals("END   ")) {
						atomNumberMap.clear();
						// create bonds and finish the molecule
						if (deduceBonding.isSet()) {
							// OK, try to deduce the bonding patterns
							if (oBP.getAtomCount() != 0) {
								// Create bonds. If bonds could not be created, all bonds are deleted.
								try {
									if (useRebondTool.isSet()) {
										if(!createBondsWithRebondTool(oBP))	{
											// Get rid of all potentially created bonds.
											logger.info("Bonds could not be created using the RebondTool when PDB file was read.");								
											oBP.removeAllBonds();								
										}
									} else {
										if(!createBonds(oBP))	{
											// Get rid of all potentially created bonds.
											logger.info("Bonds could not be created when PDB file was read.");								
											oBP.removeAllBonds();								
										}
									}
								} catch (Exception exception) {
									logger.info("Bonds could not be created when PDB file was read.");
									logger.debug(exception);
								}
							}
						}
						oSet.addMolecule(oBP);
						//						oBP = new BioPolymer();					
						//				} else if (cCol.equals("USER  ")) {
						//						System.out.println(cLine);
						//				} else if (cCol.equals("MODEL ")) {
						//					System.out.println(cLine);
						//				} else if (cCol.equals("ENDMDL")) {
						//					System.out.println(cLine);
					} else if (cCol.equals("REMARK")) {						
						Object comment = oFile.getProperty(CDKConstants.COMMENT);
                        if (comment == null) {
                        	comment = "";
                        }
                        if (lineLength >12) {
                        	comment = comment.toString() + cRead.substring(11).trim() + "\n";
                        	oFile.setProperty(CDKConstants.COMMENT, comment);
                        } else {
                        	logger.warn("REMARK line found without any comment!");
                        }
					} else if (cCol.equals("COMPND")) {						
                        String title = cRead.substring(10).trim();
                        oFile.setProperty(CDKConstants.TITLE, title);
					} 
					
					/*************************************************************
					 * Read connectivity information from CONECT records.
					 * Only covalent bonds are dealt with. Perhaps salt bridges
					 * should be dealt with in the same way..?
					 */
					else if (readConnect.isSet() && cCol.equals("CONECT"))	{
						cRead.trim();
						if (cRead.length() < 16) {
							logger.debug("Skipping unexpected empty CONECT line! : ", cRead);
						} else {
						
							String bondAtom = cRead.substring(7, 11);
							bondAtom = bondAtom.trim();
							int bondAtomNo = Integer.parseInt(bondAtom);
							String bondedAtom = cRead.substring(12, 16);
							bondedAtom = bondedAtom.trim();
							int bondedAtomNo = -1;
							
							try	{bondedAtomNo = Integer.parseInt(bondedAtom);}
							catch(Exception e)	{bondedAtomNo = -1;}
							
							if(bondedAtomNo != -1)	{
								addBond(oBP, bondAtomNo, bondedAtomNo);
								logger.warn("Bonded " + bondAtomNo + " with " + bondedAtomNo);
							}
							
							if(cRead.length() > 17)	{
								bondedAtom = cRead.substring(17, 21);
								bondedAtom = bondedAtom.trim();
								try	{bondedAtomNo = Integer.parseInt(bondedAtom);}
								catch(Exception e)	{bondedAtomNo = -1;}
								
								if(bondedAtomNo != -1)	{
									addBond(oBP, bondAtomNo, bondedAtomNo);
									logger.warn("Bonded " + bondAtomNo + " with " + bondedAtomNo);
								}
							}
							
							if(cRead.length() > 22)	{
								bondedAtom = cRead.substring(22, 26);
								bondedAtom = bondedAtom.trim();
								try	{bondedAtomNo = Integer.parseInt(bondedAtom);}
								catch(Exception e)	{bondedAtomNo = -1;}
								
								if(bondedAtomNo != -1)	{
									addBond(oBP, bondAtomNo, bondedAtomNo);
									logger.warn("Bonded " + bondAtomNo + " with " + bondedAtomNo);
								}
							}
							
							if(cRead.length() > 27)	{
								bondedAtom = cRead.substring(27, 31);
								bondedAtom = bondedAtom.trim();
								try	{bondedAtomNo = Integer.parseInt(bondedAtom);}
								catch(Exception e)	{bondedAtomNo = -1;}
								
								if(bondedAtomNo != -1)	{
									addBond(oBP, bondAtomNo, bondedAtomNo);
									logger.warn("Bonded " + bondAtomNo + " with " + bondedAtomNo);
								}
							}
						}
					}
					/*************************************************************/
					
					else if (cCol.equals("HELIX ")) {
//						HELIX    1 H1A CYS A   11  LYS A   18  1 RESIDUE 18 HAS POSITIVE PHI    1D66  72
//						          1         2         3         4         5         6         7
//						01234567890123456789012345678901234567890123456789012345678901234567890123456789
						PDBStructure structure = new PDBStructure();
						structure.setStructureType(PDBStructure.HELIX);
					    structure.setStartChainID(cRead.charAt(19));
					    structure.setStartSequenceNumber(Integer.parseInt(cRead.substring(21, 25).trim()));
					    structure.setStartInsertionCode(cRead.charAt(25));
					    structure.setEndChainID(cRead.charAt(31));
					    structure.setEndSequenceNumber(Integer.parseInt(cRead.substring(33, 37).trim()));
					    structure.setEndInsertionCode(cRead.charAt(37));
					    oBP.addStructure(structure);
					} else if (cCol.equals("SHEET ")) {
						PDBStructure structure = new PDBStructure();
						structure.setStructureType(PDBStructure.SHEET);
					    structure.setStartChainID(cRead.charAt(21));
					    structure.setStartSequenceNumber(Integer.parseInt(cRead.substring(22, 26).trim()));
					    structure.setStartInsertionCode(cRead.charAt(26));
					    structure.setEndChainID(cRead.charAt(32));
					    structure.setEndSequenceNumber(Integer.parseInt(cRead.substring(33, 37).trim()));
					    structure.setEndInsertionCode(cRead.charAt(37));
					    oBP.addStructure(structure);
					} else if (cCol.equals("TURN  ")) {
						PDBStructure structure = new PDBStructure();
						structure.setStructureType(PDBStructure.TURN);
					    structure.setStartChainID(cRead.charAt(19));
					    structure.setStartSequenceNumber(Integer.parseInt(cRead.substring(20, 24).trim()));
					    structure.setStartInsertionCode(cRead.charAt(24));
					    structure.setEndChainID(cRead.charAt(30));
					    structure.setEndSequenceNumber(Integer.parseInt(cRead.substring(31, 35).trim()));
					    structure.setEndInsertionCode(cRead.charAt(35));
					    oBP.addStructure(structure);
					}
				}
			} while (_oInput.ready() && (cRead != null));
		} catch (Exception e) {
			System.out.println("Found a problem at line:\n");
			System.out.println(cRead);
			System.out.println("01234567890123456789012345678901234567890123456789012345678901234567890123456789");
			System.out.println("          1         2         3         4         5         6         7         ");
			System.out.println("  error: " + e.getMessage());
			e.printStackTrace();
		}
		
		// try to close the Input
		try {
			_oInput.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// Set all the dependencies
		oModel.setSetOfMolecules(oSet);
		oSeq.addChemModel(oModel);
		oFile.addChemSequence(oSeq);
		
		return oFile;
	}
	
	private void addBond(PDBPolymer obp, int bondAtomNo, int bondedAtomNo) {
		IAtom firstAtom = (PDBAtom)atomNumberMap.get(new Integer(bondAtomNo));
		IAtom secondAtom = (PDBAtom)atomNumberMap.get(new Integer(bondedAtomNo));
		if (firstAtom == null) {
			logger.error("Could not find bond start atom in map with serial id: ", bondAtomNo);
		}
		if (secondAtom == null) {
			logger.error("Could not find bond target atom in map with serial id: ", bondAtomNo);
		}
		obp.addBond(firstAtom.getBuilder().newBond(firstAtom, secondAtom, 1));
	}

	/**
	 * Create bonds when reading a protein PDB file. NB ONLY works for protein
	 * PDB files! If you want to read small molecules I recommend using molecule
	 * file format where the connectivity is explicitly stated. [This method can
	 * however reasonably easily be extended to cover e.g. nucleic acids or your
	 * favourite small molecule]. Returns 'false' if bonds could not be created
	 * due to incomplete pdb-records or other reasons. 
	 * 
	 * @param pol The Biopolymer to work on
	 */
	public boolean createBonds(IBioPolymer pol){
		HashMap AAs = AminoAcids.getHashMapByThreeLetterCode();
		int[][] AABondInfo = AminoAcids.aaBondInfo();
		Hashtable strands = pol.getStrands();
		Enumeration strandKeys = strands.keys();
		
		while(strandKeys.hasMoreElements())	{
			IStrand strand = (IStrand)strands.get(strandKeys.nextElement());
			int atoms = 0;
			int atomsInLastResidue = 0;
			int atomsInPresentResidue = 0;
			
			while (atoms < strand.getAtomCount() - 1) {
				PDBAtom anAtom = (PDBAtom)strand.getAtomAt(atoms);
				
				// Check that we have bond info about residue/ligand, if not - exit.
				if(!AAs.containsKey(anAtom.getResName()))	{
					return false;
				}
				IMonomer monomer = (IMonomer)AAs.get(anAtom.getResName());
		
				atomsInPresentResidue = Integer.parseInt((String)monomer.getProperty(AminoAcids.NO_ATOMS));
				
				/* Check if there's something wrong with the residue record (e.g. it doesn't contain the
				 * correct number of atom records). */
				int counter = 1;
				while (atoms + counter < strand.getAtomCount() &&
						anAtom.getResName().equals(strand.getAtomAt(atoms + counter).getProperty(AminoAcids.RESIDUE_NAME))) {
					counter++;
				}
				// Check if something is wrong. Remember to deal with possible OXT atom...
				if(counter % atomsInPresentResidue != 0 && (atoms + counter == strand.getAtomCount() && counter % atomsInPresentResidue != 1))	{
					return false;
				}
				
				// If nothing's wrong, add bonds
				int bondID = Integer.parseInt((String)monomer.getProperty(AminoAcids.ID));
				for (int l = 0; l < Integer.parseInt((String)monomer.getProperty(AminoAcids.NO_BONDS)); l++) {
					IBond bond = pol.getBuilder().newBond(strand.getAtomAt(AABondInfo[bondID + l][1] + atoms), strand.getAtomAt(AABondInfo[bondID + l][2] + atoms), (double)(AABondInfo[bondID + l][3]));					
					pol.addBond(bond);
				}
				
				// If not first residue, connect residues
				if (atomsInLastResidue != 0)	{
					IBond bond = pol.getBuilder().newBond(strand.getAtomAt(atoms - atomsInLastResidue + 2), strand.getAtomAt(atoms), 1);					
					pol.addBond(bond);
				}
				
				atoms = atoms + atomsInPresentResidue;
				atomsInLastResidue = atomsInPresentResidue;
				
				// Check if next atom is an OXT. The reason to why this is seemingly overly complex is because
				// not all PDB-files have ending OXT. If that were the case you could just check if
				// atoms == mol.getAtomCount()...
				if(strand.getAtomCount() < atoms && ((PDBAtom)strand.getAtomAt(atoms)).getOxt())	{
//				if(strand.getAtomCount() < atoms && ((String)strand.getAtomAt(atoms).getProperty("oxt")).equals("1"))	{
					IBond bond = pol.getBuilder().newBond(strand.getAtomAt(atoms - atomsInLastResidue + 2), strand.getAtomAt(atoms), 1);					
					pol.addBond(bond);
				}
			}
		}
		return true;
	}
	
	private boolean createBondsWithRebondTool(IBioPolymer pol){
		RebondTool tool = new RebondTool(2.0, 0.5, 0.5);
		try {
//			 configure atoms
		      AtomTypeFactory factory = AtomTypeFactory.getInstance("org/openscience/cdk/config/data/jmol_atomtypes.txt", 
		    	  pol.getBuilder());
		      IAtom[] atoms = pol.getAtoms();
		      for (int i=0; i<atoms.length; i++) {
		        try {
		        	IAtomType[] types = factory.getAtomTypes(atoms[i].getSymbol());
		        	if (types.length > 0) {
		        		// just pick the first one
		        		AtomTypeManipulator.configure(atoms[i], types[0]);
		        	} else {
		        		logger.error("Could not configure atom with symbol: ", atoms[i].getSymbol());
		        	}
				} catch (Exception e) {
					logger.error("Could not configure atom (but don't care): " + e.getMessage());
					logger.debug(e);
				}
		      }
			tool.rebond(pol);
		} catch (Exception e) {
			logger.error("Could not rebond the polymer: " + e.getMessage());
			logger.debug(e);
		}
		return true;
	}

	/**
	 * Creates an <code>Atom</code> and sets properties to their values from
	 * the ATOM record. If the line is shorter than 80 characters, the information
	 * past 59 characters is treated as optional. If the line is shorter than 59
	 * characters, a <code>RuntimeException</code> is thrown.
	 *
	 * @param cLine  the PDB ATOM record.
	 * @return the <code>Atom</code> created from the record.
	 * @throws RuntimeException if the line is too short (less than 59 characters).
	 */
	private PDBAtom readAtom(String cLine) {
		// a line looks like:
		// 01234567890123456789012345678901234567890123456789012345678901234567890123456789
		// ATOM      1  O5*   C A   1      20.662  36.632  23.475  1.00 10.00      114D  45
		
		if (cLine.length() < 59) {
			throw new RuntimeException("PDBReader error during readAtom(): line too short");
		}
		String elementSymbol = cLine.substring(12, 14).trim();
		
		if (elementSymbol.length() == 2) {
			// ensure that the second char is lower case
			elementSymbol = elementSymbol.charAt(0) + elementSymbol.substring(1).toLowerCase();
		}
		String rawAtomName = cLine.substring(12, 16).trim();
		PDBAtom oAtom = new PDBAtom(elementSymbol, 
			new Point3d(new Double(cLine.substring(30, 38)).doubleValue(),
				new Double(cLine.substring(38, 46)).doubleValue(),
				new Double(cLine.substring(46, 54)).doubleValue()
			)
		);
		
        oAtom.setRecord(cLine);
        oAtom.setSerial(Integer.parseInt(cLine.substring(6, 11).trim()));
        oAtom.setName(rawAtomName.trim());
        oAtom.setAltLoc(cLine.substring(16, 17).trim());
        oAtom.setResName(cLine.substring(17, 20).trim());
        oAtom.setChainID(cLine.substring(21, 22).trim());
        oAtom.setResSeq(cLine.substring(22, 26).trim());
        oAtom.setICode(cLine.substring(26, 27).trim());
        oAtom.setAtomTypeName(oAtom.getResName()+"."+rawAtomName);
		if (cLine.length() >= 59) {
            String frag = cLine.substring(54, 60).trim();
            if (frag.length() > 0) {
                oAtom.setOccupancy(Double.parseDouble(frag));
            }
		}
		if (cLine.length() >= 65) {
            String frag = cLine.substring(60, 66).trim();
            if (frag.length() > 0) {
                oAtom.setTempFactor(Double.parseDouble(frag));
            }
		}
		if (cLine.length() >= 75) {
            oAtom.setSegID(cLine.substring(72, 76).trim());
		}
//		if (cLine.length() >= 78) {
//            oAtom.setSymbol((new String(cLine.substring(76, 78))).trim());
//		}
		if (cLine.length() >= 79) {
            String frag = cLine.substring(78, 80).trim();
            if (frag.length() > 0) {
                oAtom.setCharge(Double.parseDouble(frag));
            }
		}
		
		/*************************************************************************************
		 * It sets a flag in the property content of an atom,
		 * which is used when bonds are created to check if the atom is an OXT-record => needs
		 * special treatment.
		 */
		String oxt = cLine.substring(13, 16).trim();
		
		if(oxt.equals("OXT"))	{
			oAtom.setOxt(true);
		}
		else	{
			oAtom.setOxt(false);
		}
		/*************************************************************************************
		 * Set hetatm property flag. 
		 */
		if(cLine.substring(0,6).toUpperCase().equals("HETATM"))	{
			oAtom.setHetAtom(true);
		}
		else	{
			oAtom.setHetAtom(false);
		}
		/*************************************************************************************/
		
		return oAtom;
	}
	
	public void close() throws IOException {
		_oInput.close();
	}

    private void initIOSettings() {
        deduceBonding = new BooleanIOSetting("DeduceBonding", IOSetting.LOW,
          "Should the PDBReader deduce bonding patterns?", 
          "true");
        useRebondTool = new BooleanIOSetting("UseRebondTool", IOSetting.LOW,
          "Should the RebondTool be used (or a heuristic approach otherwise)?",
          "true");
        readConnect = new BooleanIOSetting("ReadConnectSection", IOSetting.LOW,
          "Should the CONECT be read?",
          "true");
    }
    
    public void customizeJob() {
        fireIOSettingQuestion(deduceBonding);
        fireIOSettingQuestion(useRebondTool);
        fireIOSettingQuestion(readConnect);
    }

    public IOSetting[] getIOSettings() {
        IOSetting[] settings = new IOSetting[3];
        settings[0] = deduceBonding;
        settings[1] = useRebondTool;
        settings[2] = readConnect;
        return settings;
    }

}
