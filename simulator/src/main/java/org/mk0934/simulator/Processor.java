package org.mk0934.simulator;

import org.mk0934.simulator.instructions.*;
import org.mk0934.simulator.units.*;

import javax.swing.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

/**
 * Created by Maciej Kumorek on 9/30/2014.
 */
public class Processor {

    /**
     * Register file in the processor
     */
    private RegisterFile registerFile;

    /**
     * Main memory bus
     */
    private final Memory mainMemory;

    /**
     * Program counter
     */
    private Register pc = new Register();

    /**
     * Flag indicating if the execution should continue
     */
    private boolean isRunning;

    /**
     * Buffers
     */
    private LinkedList<AluInstruction> aluInstructionsToExecute[];
    private LinkedList<MemoryInstruction> memoryInstructionsToExecute[];

    private LinkedList<EncodedInstruction> instructionsToDecode;
    private LinkedList<DecodedInstruction> instructionsToWriteBack;

    /**
     * ALU Execution units
     */
    private AluExecutionUnit executionUnits[];

    /**
     * Memory execution units
     */
    private final MemoryExecutionUnit memoryExecutionUnits[];

    /**
     * Branch execution unit
     */
    private BranchExecutionUnit branchExecutionUnit;

    /**
     * Vector execution unit
     */
    private VectorExecutionUnit vectorExecutionUnit;

    /**
     * Write-back unit
     */
    private WriteBackUnit writebackUnit;

    /**
     * No instructions executed
     */
    private int instructionExecutedCount;

    /**
     * No cycles
     */
    private int cycles = 0;

    /* Branch stats */
    private int branchesTakenNotPredicted = 0;
    private int predictedBranches = 0;
    private int correctBranches = 0;
    private int missedBranches = 0;

    /**
     * Creates new processor
     */
    public Processor(Memory memory) {

        this.mainMemory = memory;
        this.pc.setValue(0x0);
        this.registerFile = new RegisterFile();

        // Buffers
        this.aluInstructionsToExecute = new LinkedList[Globals.execution_units_num];
        this.memoryInstructionsToExecute = new LinkedList[Globals.execution_units_num];
        this.instructionsToDecode = new LinkedList<>();
        this.instructionsToWriteBack = new LinkedList<>();
        this.vectorExecutionUnit = new VectorExecutionUnit(this);

        // Initialize execution units
        this.executionUnits = new AluExecutionUnit[Globals.execution_units_num];
        this.writebackUnit = new WriteBackUnit(this.instructionsToWriteBack, this, 0);
        this.memoryExecutionUnits = new MemoryExecutionUnit[Globals.execution_units_num];

        for(int id = 0; id < Globals.execution_units_num; id++) {

            this.aluInstructionsToExecute[id] = new LinkedList<>();
            this.memoryInstructionsToExecute[id] = new LinkedList<>();

            this.executionUnits[id] = new AluExecutionUnit(this, id);
            this.memoryExecutionUnits[id] = new MemoryExecutionUnit(this, id);
        }

        BranchPredictor predictor = null;

        if(Globals.UseDynamicBranchPredictor) {
            System.out.println("Using dynamic branch predictor");
            predictor = new SaturatingCounterBranchPredictor(this);
        } else if(Globals.UseStaticBranchPredictor) {
            System.out.println("Using static branch predictor (always backwards, never forwards)");
            predictor = new TakeBackwardsBranchPredictor(this);
        } else if(Globals.UseNaiveBranchPredictor) {
            System.out.println("Using static branch predictor (always true)");
            predictor = new AlwaysTrueBranchPredictor(this);
        }

        // Initialize branch unit
        this.branchExecutionUnit = new BranchExecutionUnit(this, predictor);
    }

    /**
     * Run the processor simulation
     */
    public void run() {

        int cycleToJumpTo = -1;

        this.isRunning = true;
        while(this.isRunning) {

            // Set register dirty flag to false
            this.registerFile.commit();

            // Increment cycles
            cycles++;

            if (Globals.IsInteractive) {
                cycleToJumpTo = ProcessInteractiveInput(cycleToJumpTo);
            }

            Utilities.log("Cycle #" + cycles);

            // Write-back
            writebackUnit.writeBack();

            // Execute ALU
            for (int i = 0; i < Globals.execution_units_num; i++) {
                executionUnits[i].execute();
            }

            // Execute memory execution unit
            for (int i = 0; i < Globals.execution_units_num; i++) {
                memoryExecutionUnits[i].execute();
            }

            this.vectorExecutionUnit.execute();

            // Decode
            boolean decodedPrevious = true;
            for (int i = 0; i < Globals.execution_units_num; i++) {
                if(decodedPrevious) {
                    // Try decoding next one
                    decodedPrevious = this.decode(i);
                }
            }

            // Execution unit could have terminated
            if (!this.isRunning) {
                break;
            }

            // Fetch
            for(int i = 0; i < Globals.execution_units_num; i++) {
                this.fetch(i);
            }

            if(Globals.IsVerbose) {
                this.dumpRegisterFile(true);
            }

            if(areQueuesEmpty()) {
                isRunning = false;
            }
        }

        printStatistics();

    }

    /**
     * In the interactive mode, process parameters
     * @param cycleToJumpTo
     * @return
     */
    private int ProcessInteractiveInput(int cycleToJumpTo) {

        boolean gettingCommands = true;

        Scanner keyboard = new Scanner(System.in);

        // We might be getting a command or skipping some cycles
        while (gettingCommands && (cycles == cycleToJumpTo || cycleToJumpTo < 0)) {
            // Reset the 'break-point'
            cycleToJumpTo = -1;

            String inputLine = keyboard.nextLine().trim().toLowerCase();

            if (inputLine.equals("c") || inputLine.equals("continue")) {
                // Continue
                gettingCommands = false;
                Globals.IsInteractive = false;
            } else if (inputLine.equals("n") || inputLine.equals("next")) {
                gettingCommands = false;
            } else if (inputLine.startsWith("i ") || inputLine.startsWith("info ")) {
                if (inputLine.contains(" r")) {
                    int registerNumber = Integer.parseInt(inputLine.replaceAll("[^0-9]", ""));

                    if(registerNumber < 0 || registerNumber > 15) {
                        System.out.println("No such register. Use r0, r1, ..., r15");
                    } else {
                        System.out.println(String.format("R%d: 0x%x", registerNumber,
                                this.registerFile.getRegister(registerNumber).getValue()));
                    }
                } else if (inputLine.contains(" mem")) {
                    this.dumpMemory();
                }
            } else if (inputLine.startsWith("j ") || inputLine.startsWith("jump ")) {
                cycleToJumpTo = Integer.parseInt(inputLine.replaceAll("[^0-9]", ""));
            } else {
                System.out.println("Unknown option");
                printInteractiveUsage();
            }
        }

        return cycleToJumpTo;
    }

    /**
     * Print to user how to use interactive commandline
     */
    private void printInteractiveUsage() {
        StringBuilder sb = new StringBuilder();

        sb.append("Usage:\n");
        sb.append("\tn or next - Go to next cycle\n");
        sb.append("\tc or continue - exit interactive mode and let simulator run\n");
        sb.append("\ti arg or info arg - Display specific information\n");
        sb.append("\t\tInfo arguments:\n");
        sb.append("\t\tr0-r15 - display register value\n");
        sb.append("\t\tmem or memory - dump memory\n");
        sb.append("\tj $num or jump $num - skip to $num cycle\n");
        sb.append('\n');

        System.out.print(sb.toString());
    }

    /**
     * Checks if all queues are empty
     * @return
     */
    private boolean areQueuesEmpty() {

        return this.instructionsToDecode.isEmpty() & this.areExecuteQueuesEmpty() &
                this.isWriteBackQueueEmpty();
    }

    /**
     * Print out overall statistics
     */
    private void printStatistics() {

        this.dumpRegisterFile(false);

        this.dumpMemory();

        System.out.println("--- STATISTICS ---");

        // Cycles stats
        System.out.println(String.format("Total cycles: %d", cycles));
        System.out.println(String.format("Total instructions executed: %d", instructionExecutedCount));
        System.out.println(
                String.format("IPC (Instructions per cycle): %.3f", instructionExecutedCount / (double)cycles));
        System.out.println(
                String.format("CPI (Cycles per instruction): %.3f", cycles / (double)instructionExecutedCount));

        // Branch stats
        System.out.println("Branches stats:");
        System.out.println(String.format("\ttotal: %d", this.getTotalBranches()));
        System.out.println(String.format("\ttaken (not predicted): %d", this.branchesTakenNotPredicted));
        System.out.println(String.format("\tpredicted correctly: %d", this.correctBranches));
        System.out.println(String.format("\tpredicted missed: %d", this.missedBranches));
    }

    /**
     * Fetch stage
     * @param unitId Fetch unit id
     */
    private void fetch(int unitId) {

        String tag = String.format("FETCH(%d)", unitId);

        // Get the PC value
        int currentPcValue = this.pc.getValue();

        // Is buffer full?
        // Make sure we have enough instructions to decode later on
        if(instructionsToDecode.size() > Globals.execution_units_num) {
            // We reached memory that isn't instructions
            Utilities.log(tag, "Instruction buffer full. Skipping.");
            return;
        }

        // Encoded instruction we will try to fetch fom the memory
        EncodedInstruction currentEncodedInstruction;

        // Try to get the instruction from memory
        try {
            currentEncodedInstruction = (EncodedInstruction) this.mainMemory.getFromMemory(currentPcValue);

            Utilities.log(tag, "Fetched " + currentEncodedInstruction.getEncodedInstruction()
                    + " at address " + Integer.toHexString(currentPcValue));
        } catch (ClassCastException ex) {
            // We reached memory that isn't instructions
            Utilities.log(tag, "nothing to do");
            return;
        }

        // Increment PC
        this.pc.setValue(currentPcValue + 0x4);
        Utilities.log("\tIncremented PC to " + Integer.toHexString(this.pc.getValue()));

        instructionsToDecode.addLast(currentEncodedInstruction);
    }

    private DecodedInstruction findDependency(List<? extends DecodedInstruction> buffer,
                                   DecodedInstruction currentInstruction) {

        Integer sourceRegister1 = currentInstruction.getFirstSourceRegisterNumber();
        Integer sourceRegister2 = currentInstruction.getSecondSourceRegisterNumber();

        for (DecodedInstruction instruction : buffer) {

            Integer destinationRegister = instruction.getDestinationRegisterNumber();

            // Instruction doesn't write back anything, so no need to worry
            if (destinationRegister == null) {
                return null;
            }

            // Check if some instruction is writing back to our source registers
            if ((sourceRegister1 != null && sourceRegister1 == destinationRegister)
                    || (sourceRegister2 != null && sourceRegister2 == destinationRegister)) {


                Utilities.log("DECODE",
                        String.format("Can't decode, there's dependency in %s due to %s",
                                currentInstruction.getEncodedInstructionString(),
                                instruction.getEncodedInstructionString()));

                return instruction;
            }
        }

        return null;
    }
    /**
     * Decode stage
     */
    private boolean decode(int id) {

        String tag = String.format("DECODE(%d)", id);

        if(this.instructionsToDecode.isEmpty()) {
            Utilities.log(tag, "nothing to do");
            return false;
        }

        // Get next encoded instruction from the buffer to be decoded
        EncodedInstruction currentEncodedInstruction = this.instructionsToDecode.peek();

        Utilities.log(tag, "Decoding " + currentEncodedInstruction.getEncodedInstruction());

        // Decode fetched instruction
        DecodedInstruction currentInstruction = currentEncodedInstruction.decode(this);


        // If NOP, other queues need to be empty
        if(currentInstruction.getOperand() == Operand.NOP
            && (!this.isWriteBackQueueEmpty() || !this.areExecuteQueuesEmpty())) {

            // Stall, we need to wait for the result
            Utilities.log(tag, "Can't NOP just yet");
            return false;
        }

        // Is there a blocking dependency?
        boolean isBlocked = false;
        DecodedInstruction blockingInstruction = null;

        // Check memory buffer
        for(List<MemoryInstruction> buffer : this.memoryInstructionsToExecute) {

            DecodedInstruction blocking = findDependency(buffer, currentInstruction);

            if (blocking != null) {
                // Stall, we need to wait for the result
                isBlocked = true;
                blockingInstruction = blocking;
                break;
            }
        }

        // Check if there is a dependency in ALU
        for(List<AluInstruction> buffer : this.aluInstructionsToExecute) {

            DecodedInstruction blocking = findDependency(buffer, currentInstruction);

            if(blocking != null && !isBlocked) {
                // Stall, we need to wait for the result
                isBlocked = true;
                blockingInstruction = blocking;
                break;
            }
        }

        // Check vector instruction buffer
        if(!this.vectorExecutionUnit.getReservationStation().isEmpty()) {

            DecodedInstruction blocking = findDependency(this.vectorExecutionUnit.getReservationStation(), currentInstruction);

            if(blocking != null && !isBlocked) {
                // Stall, we need to wait for the result
                isBlocked = true;
                blockingInstruction = blocking;
            }
        }

        // Check write back buffer
        if(!isWriteBackQueueEmpty()) {

            DecodedInstruction blocking = findDependency(this.instructionsToWriteBack, currentInstruction);

            if(blocking != null && !isBlocked)
            {
                // Stall, we need to wait for the result
                isBlocked = true;
                blockingInstruction = blocking;
            }
        }

        // Check if it's a branch, if so, take try it here
        if(currentInstruction instanceof BranchInstruction) {

            BranchInstruction branchInstruction = (BranchInstruction)currentInstruction;

            if(!isBlocked) {

                // Just take a branch based on actual values
                branchExecutionUnit.execute(branchInstruction);
                instructionsToDecode.remove(currentEncodedInstruction);
            } else {

                // Otherwise try to guess
                branchExecutionUnit.predictAndExecute(branchInstruction, blockingInstruction);
                instructionsToDecode.remove(currentEncodedInstruction);
            }
        }

        if(isBlocked) {
            return false;
        }

        if(currentInstruction instanceof AluInstruction) {

            // Add ALU to the buffer
            this.aluInstructionsToExecute[id].addLast((AluInstruction)currentInstruction);

            // Successfully decoded
            this.instructionsToDecode.remove(currentEncodedInstruction);

            return true;

        } else if(currentInstruction instanceof MemoryInstruction) {

            // Or memory buffer
            this.memoryInstructionsToExecute[id].addLast((MemoryInstruction)currentInstruction);

            // Successfully decoded
            this.instructionsToDecode.remove(currentEncodedInstruction);

            return true;
        } else if(currentInstruction instanceof VectorInstruction) {

            // Add to the reservation station
            this.vectorExecutionUnit.getReservationStation().addLast((VectorInstruction)currentInstruction);

            // Successfully decoded
            this.instructionsToDecode.remove(currentEncodedInstruction);
        }

        return false;
    }

    private boolean isWriteBackQueueEmpty() {
        return this.instructionsToWriteBack.isEmpty();
    }

    private boolean areExecuteQueuesEmpty() {

        boolean result = true;

        for (LinkedList<?> queue : aluInstructionsToExecute) {
            result = result & queue.isEmpty();
        }

        for (LinkedList<?> queue : memoryInstructionsToExecute) {
            result = result & queue.isEmpty();
        }

        return result;
    }

    /**
     * Set running flag, useful for termination
     * @param running
     */
    public void setRunning(boolean running) {
        this.isRunning = running;
    }

    /**
     * Gets the register file
     * @return
     */
    public final RegisterFile getRegisterFile() {
        return this.registerFile;
    }

    public final Memory getMemory() {
        return this.mainMemory;
    }

    public Register getPc() {
        return this.pc;
    }

    public void dumpMemory() {

        System.out.println("Memory dump: ");
        for(int i = 0; i < this.getMemory().getMaxAddress(); i += 0x4) {
            System.out.println("Addr: 0x" + Integer.toHexString(i)
                    + " " + this.getMemory().getFromMemory(i).toString());
        }

    }

    public void dumpRegisterFile(boolean dirtyOnly) {

        // Dump registers
        final RegisterFile registerFile = this.getRegisterFile();

        if(dirtyOnly) {
            System.out.println("Registers that changed:");
        } else {
            System.out.println("Register file dump: ");
        }

        for(int i = 0; i < registerFile.getCount(); i++) {
            Register register = registerFile.getRegister(i);

            if(!register.getDirty() && dirtyOnly) {
                continue;
            }

            int value = register.getValue();
            System.out.print("R" + String.format("%02d", i) + ":\t0x" + Integer.toHexString(value).toUpperCase());

            if((i + 1) % 4 == 0) {
                System.out.print("\n");
            } else {
                System.out.print("\t\t");
            }
        }

        if(dirtyOnly && !getPc().getDirty()) {
            return;
        }

        System.out.println("PC:\t0x" + Integer.toHexString(this.getPc().getValue()).toUpperCase());
    }

    public void incrementInstructionCounter() {
        this.instructionExecutedCount += 1;
    }

    /**
     * Returns the buffer containing next memory instructions to be executed
     * @param id Execution unit id
     * @return
     */
    public final LinkedList<MemoryInstruction> getMemoryInstructionsToExecute(int id) {
        return this.memoryInstructionsToExecute[id];
    }

    /**
     * Return write back buffer
     */
    public final LinkedList<DecodedInstruction> getWriteBackBuffer() {
        return this.instructionsToWriteBack;
    }

    /**
     * Return alu instructions scheduled to execute
     * @param id Execution unit id
     * @return
     */
    public LinkedList<AluInstruction> getAluInstructionsBuffer(int id) {
        return this.aluInstructionsToExecute[id];
    }

    public void incrementCorrectBranches() {
        this.correctBranches++;
    }

    public void IncrementMissedBranches() {
        this.missedBranches++;
    }

    public void incrementBranchCounter() {
        this.branchesTakenNotPredicted++;
    }

    public int getTotalBranches() {
        return this.branchesTakenNotPredicted + correctBranches + missedBranches;
    }

    /**
     * @return List of instructions waiting for decode
     */
    public LinkedList<EncodedInstruction> getDecodeBuffer() {
        return this.instructionsToDecode;
    }
}
