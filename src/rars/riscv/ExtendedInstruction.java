package rars.riscv;

import rars.RISCVprogram;
import rars.assembler.Symbol;
import rars.assembler.TokenList;
import rars.util.Binary;

import java.util.ArrayList;
import java.util.StringTokenizer;

	/*
Copyright (c) 2003-2008,  Pete Sanderson and Kenneth Vollmar

Developed by Pete Sanderson (psanderson@otterbein.edu)
and Kenneth Vollmar (kenvollmar@missouristate.edu)

Permission is hereby granted, free of charge, to any person obtaining 
a copy of this software and associated documentation files (the 
"Software"), to deal in the Software without restriction, including 
without limitation the rights to use, copy, modify, merge, publish, 
distribute, sublicense, and/or sell copies of the Software, and to 
permit persons to whom the Software is furnished to do so, subject 
to the following conditions:

The above copyright notice and this permission notice shall be 
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF 
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR 
ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION 
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(MIT license, http://www.opensource.org/licenses/mit-license.html)
 */

/**
 * ExtendedInstruction represents a RISCV extended (a.k.a pseudo) instruction.  This
 * assembly language instruction does not have a corresponding machine instruction.  Instead
 * it is translated by the extended assembler into one or more basic instructions (operations
 * that have a corresponding machine instruction).
 *
 * @author Pete Sanderson
 * @version August 2003
 */
public class ExtendedInstruction extends Instruction {

    private final ArrayList<String> translationStrings;

    /**
     * Constructor for ExtendedInstruction.  No compact translation is provided.
     *
     * @param example     A String containing example use of the MIPS extended instruction.
     * @param translation Specifications for translating this instruction into a sequence
     *                    of one or more MIPS basic instructions.
     * @param description a helpful description to be included on help requests
     **/

    public ExtendedInstruction(String example, String translation, String description) {
        this.exampleFormat = example;
        this.description = description;
        this.mnemonic = this.extractOperator(example);
        this.createExampleTokenList();
        this.translationStrings = buildTranslationList(translation);
    }

    /**
     * Constructor for ExtendedInstruction, where no instruction description or
     * compact translation is provided.
     *
     * @param example     A String containing example use of the MIPS extended instruction.
     * @param translation Specifications for translating this instruction into a sequence
     *                    of one or more MIPS basic instructions.
     **/

    public ExtendedInstruction(String example, String translation) {
        this(example, translation, "");
    }

    /**
     * Given a basic instruction template and the list of tokens from an extended
     * instruction statement, substitute operands from the token list appropriately into the
     * template to generate the basic statement.  Assumes the extended instruction statement has
     * been translated from source form to basic assembly form (e.g. register mnemonics
     * translated to corresponding register numbers).
     * Operand format of source statement is already verified correct.
     * Assume the template has correct number and positions of operands.
     * Template is String with special markers.  In the list below, n represents token position (1,2,3,etc)
     * in source statement (operator is token 0, parentheses count but commas don't):
     * <UL>
     * <LI>RGn means substitute register found in n'th token of source statement
     * <LI>LLn means substitute low order 16 bits from label address in source token n.
     * <LI>LHn means substitute high order 16 bits from label address in source token n. Must add 1 if address bit 15 is 1.
     * <LI>PCLn is similar to LLn except the value substituted will be relative to PC of the psuedo-op that generated it.
     * <LI>PCHn is similar to LHn except the value substituted will be relative to PC of the psuedo-op that generated it.
     * <LI>VLn means substitute low order 16 bits from 32 bit value in source token n.
     * <LI>VHn means substitute high order 16 bits from 32 bit value in source token n, then add 1 if value's bit 15 is 1.
     * <LI>LAB means substitute textual label from last token of source statement.  Used for various branches.
     * </UL>
     *
     * @param template  a String containing template for basic statement.
     * @param tokenList a TokenList containing tokens from extended instruction.
     * @return String representing basic assembler statement.
     */

    public static String makeTemplateSubstitutions(RISCVprogram program, String template, TokenList tokenList, int PC) {
        String instruction = template;
        // substitute first operand token for template's RG1 or OP1, second for RG2 or OP2, etc
        for (int op = 1; op < tokenList.size(); op++) {
            instruction = substitute(instruction, "RG" + op, tokenList.get(op).getValue());

            String strValue = tokenList.get(op).getValue();
            int val;
            try {
                val = Binary.stringToInt(strValue);    // KENV   1/6/05
            } catch (NumberFormatException e) {
                long lval;
                try {
                    lval = Binary.stringToLong(strValue);
                } catch (NumberFormatException nfe) {
                    continue;
                }
                val = (int) (lval >> 32);
                int vall = (int) lval;
                // this shouldn't happen if is is for LL .. VH
                if (instruction.contains("LIA" + op)) {
                    int extra = Binary.bitValue(val, 11); // add extra to compesate for sign extension
                    instruction = substitute(instruction, "LIA" + op, String.valueOf((val >> 12) + extra));
                } else if (instruction.contains("LIB" + op)) {
                    instruction = substitute(instruction, "LIB" + op, String.valueOf(val << 20 >> 20));
                } else if (instruction.contains("LIC" + op)) {
                    instruction = substitute(instruction, "LIC" + op, String.valueOf((vall >> 21) & 0x7FF));
                } else if (instruction.contains("LID" + op)) {
                    instruction = substitute(instruction, "LID" + op, String.valueOf((vall >> 10) & 0x7FF));
                } else if (instruction.contains("LIE" + op)) {
                    instruction = substitute(instruction, "LIE" + op, String.valueOf(vall & 0x3FF));
                }
                continue;
            }

            int relative = val - PC;
            if (instruction.contains("PCH" + op)) {
                int extra = Binary.bitValue(relative, 11);// add extra to compesate for sign extension
                instruction = substitute(instruction, "PCH" + op, String.valueOf((relative >> 12) + extra));
            }
            if (instruction.contains("PCL" + op)) {
                instruction = substitute(instruction, "PCL" + op, String.valueOf(relative << 20 >> 20));
            }

            if (instruction.contains("LH" + op)) {
                int extra = Binary.bitValue(val, 11);// add extra to compesate for sign extension
                instruction = substitute(instruction, "LH" + op, String.valueOf((val >> 12) + extra));
            }
            if (instruction.contains("LL" + op)) {
                instruction = substitute(instruction, "LL" + op, String.valueOf(val << 20 >> 20));
            }

            if (instruction.contains("VH" + op)) {
                int extra = Binary.bitValue(val, 11); // add extra to compesate for sign extension
                instruction = substitute(instruction, "VH" + op, String.valueOf((val >> 12) + extra));
            }
            if (instruction.contains("VL" + op)) {
                instruction = substitute(instruction, "VL" + op, String.valueOf(val << 20 >> 20));
            }
        }
        // substitute label if necessary
        if (instruction.contains("LAB")) {
            // label has to be last token.  It has already been translated to address
            // by symtab lookup, so I need to get the text label back so parseLine() won't puke.
            String label = tokenList.get(tokenList.size() - 1).getValue();
            Symbol sym = program.getLocalSymbolTable().getSymbolGivenAddressLocalOrGlobal(label);
            if (sym != null) {
                // should never be null, since there would not be an address if label were not in symtab!
                // DPS 9 Dec 2007: The "substitute()" method will substitute for ALL matches.  Here
                // we want to substitute only for the first match, for two reasons: (1) a statement
                // can only contain one label reference, its last operand, and (2) If the user's label
                // contains the substring "LAB", then substitute() will go into an infinite loop because
                // it will keep matching the substituted string!
                instruction = substituteFirst(instruction, "LAB", sym.getName());
            }
        }
        return instruction;
    }

    // TODO: update these two methods to Java 1.8
    // Performs a String substitution.  Java 1.5 adds an overloaded String.replace method to
    // do this directly but I wanted to stay 1.4 compatible.
    // Modified 12 July 2006 to "substitute all occurances", not just the first.
    private static String substitute(String original, String find, String replacement) {
        if (!original.contains(find) || find.equals(replacement)) {
            return original;  // second condition prevents infinite loop below
        }
        int i;
        String modified = original;
        while ((i = modified.indexOf(find)) >= 0) {
            modified = modified.substring(0, i) + replacement + modified.substring(i + find.length());
        }
        return modified;

    }

    // Performs a String substitution, but will only substitute for the first match.
    // Java 1.5 adds an overloaded String.replace method to do this directly but I
    // wanted to stay 1.4 compatible.
    private static String substituteFirst(String original, String find, String replacement) {
        if (!original.contains(find) || find.equals(replacement)) {
            return original;  // second condition prevents infinite loop below
        }
        int i;
        String modified = original;
        if ((i = modified.indexOf(find)) >= 0) {
            modified = modified.substring(0, i) + replacement + modified.substring(i + find.length());
        }
        return modified;
    }

    /**
     * Get length in bytes that this extended instruction requires in its
     * binary form. The answer depends on how many basic instructions it
     * expands to.
     *
     * @return int length in bytes of corresponding binary instruction(s).
     */

    public int getInstructionLength() {
        return getInstructionLength(translationStrings);
    }

    /**
     * Get ArrayList of Strings that represent list of templates for
     * basic instructions generated by this extended instruction.
     *
     * @return ArrayList of Strings.
     */

    public ArrayList<String> getBasicIntructionTemplateList() {
        return translationStrings;
    }


    // Takes list of basic instructions that this extended instruction
    // expands to, which is a string, and breaks out into separate
    // instructions.  They are separated by '\n' character.

    private ArrayList<String> buildTranslationList(String translation) {
        if (translation == null || translation.length() == 0) {
            return null;
        }
        ArrayList<String> translationList = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(translation, "\n");
        while (st.hasMoreTokens()) {
            translationList.add(st.nextToken());
        }
        return translationList;
    }


    /*
     * Get length in bytes that this extended instruction requires in its
     * binary form. The answer depends on how many basic instructions it
     * expands to.
     * Returns length in bytes of corresponding binary instruction(s).
     * Returns 0 if the ArrayList is null or empty.
     */
    private int getInstructionLength(ArrayList<String> translationList) {
        if (translationList == null || translationList.size() == 0) {
            return 0;
        }
        return 4 * translationList.size();
    }
}