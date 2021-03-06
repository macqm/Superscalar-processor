package org.mk0934.simulator.instructions;

import org.mk0934.simulator.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Maciej Kumorek on 10/24/2014.
 */
public class EncodedInstruction extends Instruction {

    final Pattern registerPattern = Pattern.compile("r[0-9]+");
    final Pattern interValPattern = Pattern.compile("(:?0x)?([0-9a-fA-F]{1,8})");

    public EncodedInstruction(String instructionString) {

        this.encodedInstruction = this.parseEncodedInstruction(instructionString);
    }

    public EncodedInstruction(String instructionString, String label) {
        super(label);
        this.encodedInstruction = this.parseEncodedInstruction(instructionString);
    }

    protected String parseEncodedInstruction(String instructionString) {
        if(!instructionString.contains(";")) {
            return instructionString;
        }

        int indexOfSemicolon = instructionString.indexOf(';');
        return instructionString.substring(0, indexOfSemicolon).trim();
    }

    public void replaceLabelWithAddress(String label, Integer integer) {
        this.encodedInstruction = this.encodedInstruction.replace(label, "0x" + Integer.toHexString(integer));
    }

    /**
     * Factory of instructions
     * @return instance of Decoded instruction
     */
    @Override
    public DecodedInstruction decode(Processor processor) {

        // Find the operand in the string
        Operand operand = this.parseOperand(this.encodedInstruction);

        // Get register file
        RegisterFile registerFile = processor.getRegisterFile();

        // Choose decoding logic for an operation
        if(operand == Operand.NOP) {

            return new NopInstruction(operand, this);
        } else if(operand == Operand.ADD) {
            // Decode ADD
            return this.decodeAdd(registerFile);
        } else if(operand == Operand.MUL) {
            // Decode MUL
            return this.decodeMul(registerFile);
        } else if(operand == Operand.MOV) {
            // Decode MOV
            return this.decodeMov(registerFile);
        } else if(operand == Operand.SUB) {
            // Decode SUB
            return this.decodeSub(registerFile);
        } else if(operand == Operand.LDM) {
            // Decode LDM - memory load
            return this.decodeLoadMemory(registerFile);
        } else if(operand == Operand.STM) {
            // Decode STM - memory store
            return this.decodeStoreMemory(registerFile);
        } else if(operand == Operand.CMP) {
            // Decode CMP
            return this.decodeCmp(registerFile);
        } else if(operand == Operand.BGE) {
            // Decode BGE - Branch if greater or equal
            return this.decodeBranchGreaterEqual(registerFile);
        } else if(operand == Operand.BGT) {
            // Decode BGT - Branch if greater - than
            return this.decodeBranchGreaterThan(registerFile);
        } else if(operand == Operand.BEQ) {
            // Decode BEQ - Branch if equal
            return this.decodeBranchEqual(registerFile);
        } else if(operand == Operand.JMP) {
            // Decode JMP - Jump instruction
            return this.decodeJmp();
        } else if(operand == Operand.VLDM) {
            // Decode VLDM - Vector load memory
            return this.decodeVectorLoad(registerFile);
        } else if(operand == Operand.VMUL) {
            return this.decodeVectorMultiply(registerFile);
        } else if(operand == Operand.VSTM) {
            return this.decodeVectorStore(registerFile);
        }

        throw new RuntimeException("Cannot decode instruction with operand: " + operand);
    }

    /**
     * Decode VSTM
     *
     * Vectore store memory
     *
     * @param registerFile
     * @return
     */
    private DecodedInstruction decodeVectorStore(RegisterFile registerFile) {

        Integer[] threeArgs = this.getThreeParams(registerFile);
        Integer[] args = new Integer[threeArgs.length + 2];

        // No destination register
        args[0] = null;

        // Actually first register is not destination, so we need to get its value rather than number
        args[1] = registerFile.getRegister(threeArgs[0]).getValue();
        args[4] = threeArgs[0];

        args[2] = threeArgs[1];
        args[5] = threeArgs[3];

        args[3] = threeArgs[2];
        args[6] = threeArgs[4];

        return new VectorStoreMemoryInstruction(args, this);
    }

    /**
     * Decode VMUL
     * Vector multiplication
     * @param registerFile
     * @return
     */
    private DecodedInstruction decodeVectorMultiply(RegisterFile registerFile) {
        Integer[] args = this.getThreeParams(registerFile);
        return new VectorMultiplyInstruction(args, this);
    }

    /**
     * Decode VLDM
     *
     * Vector Load memory
     * @param registerFile processor's register file
     * @return instance of VectorLoadInstruction
     */
    private DecodedInstruction decodeVectorLoad(RegisterFile registerFile) {
        Integer[] args = this.getThreeParams(registerFile);
        return new VectorLoadInstruction(args, this);
    }

    /**
     * Decode JMP
     *
     * JMP takes only one argument, absolute address to jump to
     * @return JumpInstructon instance
     */
    private DecodedInstruction decodeJmp() {
        int address = this.getImmediateParam();
        return new JumpInstruction(address, this);
    }

    private DecodedInstruction decodeBranchGreaterEqual(RegisterFile registerFile) {

        Integer[] args = this.getTwoArgValues(registerFile);
        return new BranchGreaterEqualInstruction(args, this);
    }

    private DecodedInstruction decodeBranchGreaterThan(RegisterFile registerFile) {
        Integer[] args = this.getTwoArgValues(registerFile);
        return new BranchGreaterThanInstruction(args, this);
    }

    private DecodedInstruction decodeBranchEqual(RegisterFile registerFile) {
        Integer[] args = this.getTwoArgValues(registerFile);
        return new BranchEqualInstruction(args, this);
    }

    /**
     * Find single immediate argument value
     * @return immediate value parsed
     */
    private int getImmediateParam() {

        // Try to get immediate value
        Matcher intermediateValMatcher = interValPattern.matcher(this.getEncodedInstruction());

        if(!intermediateValMatcher.find()) {
            throw new RuntimeException("Second source register or immediate should be specified");
        }

        return this.getImmediateValueFromString(intermediateValMatcher);
    }

    private Integer[] getThreeParams(RegisterFile registerFile) {
        return this.getThreeParams(registerFile, true, true, true);
    }

    private Integer[] getThreeParams(RegisterFile registerFile,
                                     boolean destinationRequired,
                                     boolean firstSourceRequired,
                                     boolean secondSourceRequired) {

        Integer[] params = new Integer[5];

        String input = this.getEncodedInstruction();
        String[] inputParts = input.split(",");

        // Get destination register
        Matcher matcher = registerPattern.matcher(inputParts[0]);
        Matcher intermediateValMatcher = interValPattern.matcher(inputParts[0]);
        String destinationRegisterName, sourceRegisterName;

        if (matcher.find()) {
            destinationRegisterName = matcher.group(0);
            params[0] = this.getRegisterNumberFromString(destinationRegisterName);
        } else if (destinationRequired) {
            // Destination must be specified
            throw new RuntimeException("Destination register not specified in instruction: "
                    + this.getEncodedInstruction());
        } else {
            params[0] = null;
        }

        matcher = registerPattern.matcher(inputParts[1]);
        intermediateValMatcher = interValPattern.matcher(inputParts[1]);

        // Get First source register
        if (matcher.find()) {
            sourceRegisterName = matcher.group(0);
            int registerNumber = this.getRegisterNumberFromString(sourceRegisterName);
            int valueInRegisterOne = registerFile.getRegister(registerNumber).getValue();
            params[1] = valueInRegisterOne;
            params[3] = registerNumber;
        } else if (intermediateValMatcher.find()) {
            params[1] = getImmediateValueFromString(intermediateValMatcher);
            params[3] = null;
        } else if (firstSourceRequired) {
            throw new RuntimeException("First argument should be source register");
        } else {
            params[1] = null;
            params[3] = null;
        }


        if (inputParts.length > 2) {

            matcher = registerPattern.matcher(inputParts[2]);
            intermediateValMatcher = interValPattern.matcher(inputParts[2]);

            // Get second register or immediate value
            if (matcher.find()) {
                sourceRegisterName = matcher.group(0);
                int registerNumber = this.getRegisterNumberFromString(sourceRegisterName);
                int valueInRegister = registerFile.getRegister(registerNumber).getValue();
                params[2] = valueInRegister;
                params[4] = registerNumber;
                return params;
            } else if (intermediateValMatcher.find()) {
                params[2] = getImmediateValueFromString(intermediateValMatcher);
                params[4] = null;
            } else if (secondSourceRequired) {
                throw new RuntimeException("Second source register or immediate should be specified");
            } else {
                params[2] = null;
                params[4] = null;
            }
        }

        return params;
    }

    private int getImmediateValueFromString(Matcher intermediateValMatcher) {
        String hexNumberStr = intermediateValMatcher.group(0);
        return Memory.tryParse(hexNumberStr);
    }

    private int getRegisterNumberFromString(String registerName) {
        if(registerName.startsWith("r")) {
            return Integer.parseInt(registerName.substring(1));
        }

        return Integer.parseInt(registerName);
    }

    /**
     * Decode ADD instruction.
     *
     * ADD must specify destination register
     * ADD can take two registers
     * or one register and intermediate value
     * @param registerFile processor's register file
     */
    private DecodedInstruction decodeAdd(RegisterFile registerFile) {

        Integer[] args = this.getThreeParams(registerFile);
        return new AddInstruction(args, this);
    }

    /**
     * Decode MUL instruction
     *
     * MUL must specify destination register
     * MUL can take two registers or a register and immediate
     * @param registerFile
     * @return
     */
    private DecodedInstruction decodeMul(RegisterFile registerFile) {
        Integer[] args = this.getThreeParams(registerFile);
        return new MultiplyInstruction(args, this);
    }

    /**
     * Decode SUB instruction
     *
     * SUB must specify destination register
     * SUB can take two registers or a register and immediate
     * @param registerFile
     * @return
     */
    private DecodedInstruction decodeSub(RegisterFile registerFile) {
        Integer[] args = this.getThreeParams(registerFile);
        return new SubInstruction(args, this);
    }

    /**
     * Decode LDM instruction
     *
     * LDM must specify destination register
     * LDM can take two registers or a register and immediate
     * @param registerFile
     * @return
     */
    private DecodedInstruction decodeLoadMemory(RegisterFile registerFile) {
        Integer[] args = this.getThreeParams(registerFile);
        return new LoadMemoryInstruction(args, this);
    }

    /**
     * Decode STM instruction
     *
     * STM must register with the value to store
     * STM can take two registers or a register and immediate
     * @param registerFile Register file to look for values at
     * @return
     */
    private DecodedInstruction decodeStoreMemory(RegisterFile registerFile) {
        Integer[] threeArgs = this.getThreeParams(registerFile);
        Integer[] args = new Integer[threeArgs.length + 2];

        // No destination register
        args[0] = null;

        // Actually first register is not destination, so we need to get its value rather than number
        args[1] = registerFile.getRegister(threeArgs[0]).getValue();
        args[4] = threeArgs[0];

        args[2] = threeArgs[1];
        args[5] = threeArgs[3];

        args[3] = threeArgs[2];
        args[6] = threeArgs[4];

        return new StoreMemoryInstruction(args, this);
    }

    private DecodedInstruction decodeMov(RegisterFile registerFile) {

        Integer[] args = this.getThreeParams(registerFile, true, true, false);

        return new MoveInstruction(args, this);
    }

    private DecodedInstruction decodeCmp(RegisterFile registerFile) {

        Integer[] args = this.getThreeParams(registerFile);

        return new CompareInstruction(args, this);
    }

    private Integer[] getTwoArgValues(RegisterFile registerFile) {

        String[] input = this.getEncodedInstruction().split(",");

        // Try to get immediate value
        Matcher intermediateValMatcher = interValPattern.matcher(input[0]);
        Matcher matcher = registerPattern.matcher(input[0]);

        Integer[] args = new Integer[5];
        args[4] = null; // Only two arguments

        String registerName;
        int registerNumber;



        if(matcher.find()) {
            registerName = matcher.group(0);
            registerNumber = this.getRegisterNumberFromString(registerName);
            args[0] = registerFile.getRegister(registerNumber).getValue();
            args[2] = registerNumber;
        } else if(intermediateValMatcher.find()) {
            int immediateValue = getImmediateValueFromString(intermediateValMatcher);
            args[0] = immediateValue;
            args[2] = null;
        } else {
            // LHS must be specified
            throw new RuntimeException("LHS register not specified in instruction: "
                    + this.getEncodedInstruction());
        }

        // Parse next part of the input
        intermediateValMatcher = interValPattern.matcher(input[1]);
        matcher = registerPattern.matcher(input[1]);

        // Get second register or immediate value
        if(matcher.find()) {
            registerName = matcher.group(0);
            registerNumber = this.getRegisterNumberFromString(registerName);
            args[1] = registerFile.getRegister(registerNumber).getValue();
            args[3] = registerNumber;
        } else if(intermediateValMatcher.find()) {
            int immediateValue = getImmediateValueFromString(intermediateValMatcher);
            args[1] = immediateValue;
            args[3] = null;
        } else {
            // RHS must be specified
            throw new RuntimeException("RHS register not specified in instruction: "
                    + this.getEncodedInstruction());
        }

        return args;
    }
}
