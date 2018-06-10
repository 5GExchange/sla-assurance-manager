/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fivegex.sla;

/**
 *
 * @author uceeftu
 */
public class ViolationAnalyserException extends Exception {

    /**
     * Creates a new instance of <code>ViolationAnalyserException</code> without
     * detail message.
     */
    public ViolationAnalyserException() {
    }

    /**
     * Constructs an instance of <code>ViolationAnalyserException</code> with
     * the specified detail message.
     *
     * @param msg the detail message.
     */
    public ViolationAnalyserException(String msg) {
        super(msg);
    }
}
