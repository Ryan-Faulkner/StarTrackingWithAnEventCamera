/*
 * PolarityEvent.java
 *
 * Created on May 27, 2006, 11:48 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 * Copyright May 27, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.event;

/**
 * Represents an event with a polarity that is On or Off type
 *
 * @author tobi
 */
public class PolarityEvent extends TypedEvent implements PolarityEventInterface {

	/** The polarity types */
	public enum Polarity {
		On,
		Off
	}

	/** The event polarity, either <code>On</code> for brighter or <code>Off</code> for darker */
	public Polarity polarity = Polarity.On;

	/** Creates a new instance of PolarityEvent */
	public PolarityEvent() {
	}

	@Override
	public String toString() {
		return super.toString() + " polarity=" + polarity;
	}

	@Override
	public void reset() {
		super.reset();

		polarity = Polarity.On;
	}

	/**
	 * copies fields from source event src to this event
	 *
	 * @param src
	 *            the event to copy from
	 */
	@Override
	public void copyFrom(final BasicEvent src) {
		final PolarityEvent e = (PolarityEvent) src;
		super.copyFrom(e);

		polarity = e.polarity;
	}

	/** Returns two cell types */
	@Override
	public int getNumCellTypes() {
		return 2;
	}

	/**
	 * Returns an int for the polarity, 0 for Off and 1 for On.
	 *
	 * @return 0 for Off and 1 for On
	 */
	@Override
	public int getType() {
		return polarity == Polarity.Off ? 0 : 1;
	}

	/**
	 * @return the polarity
	 */
	@Override
	public Polarity getPolarity() {
		return polarity;
	}

	/**
	 * @param polarity
	 *            the polarity to set
	 */
	@Override
	public void setPolarity(final Polarity polarity) {
		this.polarity = polarity;
	}

	/**
	 * Returns +1 if polarity is On or -1 if polarity is Off.
	 *
	 * @return +1 from On event, -1 from Off event.
	 */
	@Override
	public int getPolaritySignum() {
		switch (polarity) {
			case Off:
				return -1;
			case On:
				return +1;
		}
		throw new Error("Events should never have undefined Polarity. We should never get here.");
	}
}
