package eu.seebetter.ini.chips.davis;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import ch.unizh.ini.jaer.config.spi.SPIConfigBit;
import ch.unizh.ini.jaer.config.spi.SPIConfigValue;
import net.sf.jaer.biasgen.AddressedIPotArray;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;

/**
 * Base configuration for Davis208PixelParade on Tower wafer designs
 *
 * @author Diederik Paul Moeys, Luca Longinotti
 */
public class Davis208PixelParadeConfig extends DavisTowerBaseConfig {

	public Davis208PixelParadeConfig(final Chip chip) {
		super(chip);
		setName("Davis208PixelParadeConfig");

		ipots = new AddressedIPotArray(this);

		// VDAC biases
		ipots.addPot(new TowerOnChip6BitVDAC(this, "apsOverflowLevel", 0, 0,
			"Sets reset level gate voltage of APS reset FET to prevent overflow causing DVS events"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "ApsCas", 1, 0,
			"n-type cascode for protecting drain of DVS photoreceptor log feedback FET from APS transients"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "ADC_RefHigh", 2, 0, "on-chip column-parallel APS ADC upper conversion limit"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "ADC_RefLow", 3, 0, "on-chip column-parallel APS ADC ADC lower limit"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "AdcTestVoltage", 4, 0, "Voltage supply for testing the ADC"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "ResetHpxBv", 6, 0, "High voltage to be kept for the Hp pixel of Sim Bamford"));
		ipots.addPot(new TowerOnChip6BitVDAC(this, "RefSsbxBv", 7, 0,
			"Set OffsetBns, the shifted source bias voltage of the pre-amplifier with VDAC"));

		// CoarseFine biases
		DavisConfig.addAIPot(ipots, this, "LocalBufBn,8,n,normal,Local buffer bias");
		DavisConfig.addAIPot(ipots, this, "PadFollBn,9,n,normal,Follower-pad buffer bias current");
		diff = DavisConfig.addAIPot(ipots, this, "DiffBn,10,n,normal,differencing amp");
		diffOn = DavisConfig.addAIPot(ipots, this, "OnBn,11,n,normal,DVS brighter threshold");
		diffOff = DavisConfig.addAIPot(ipots, this, "OffBn,12,n,normal,DVS darker threshold");
		DavisConfig.addAIPot(ipots, this, "PixInvBn,13,n,normal,Pixel request inversion static inverter bias");
		pr = DavisConfig.addAIPot(ipots, this, "PrBp,14,p,normal,Photoreceptor bias current");
		sf = DavisConfig.addAIPot(ipots, this, "PrSFBp,15,p,normal,Photoreceptor follower bias current (when used in pixel type)");
		refr = DavisConfig.addAIPot(ipots, this, "RefrBp,16,p,normal,DVS refractory period current");
		DavisConfig.addAIPot(ipots, this, "ReadoutBufBP,17,p,normal,APS readout OTA follower bias");
		DavisConfig.addAIPot(ipots, this, "ApsROSFBn,18,n,normal,APS readout source follower bias");
		DavisConfig.addAIPot(ipots, this, "ADCcompBp,19,p,normal,ADC comparator bias");
		DavisConfig.addAIPot(ipots, this, "ColSelLowBn,20,n,normal,Column arbiter request pull-down");
		DavisConfig.addAIPot(ipots, this, "DACBufBp,21,p,normal,Row request pull up");
		DavisConfig.addAIPot(ipots, this, "LcolTimeoutBn,22,n,normal,No column request timeout");
		DavisConfig.addAIPot(ipots, this, "AEPdBn,23,n,normal,Request encoder pulldown static current");
		DavisConfig.addAIPot(ipots, this, "AEPuXBp,24,p,normal,AER column pullup");
		DavisConfig.addAIPot(ipots, this, "AEPuYBp,25,p,normal,AER row pullup");
		DavisConfig.addAIPot(ipots, this, "IFRefrBn,26,n,normal,Bias calibration refractory period bias current");
		DavisConfig.addAIPot(ipots, this, "IFThrBn,27,n,normal,Bias calibration neuron threshold");
		DavisConfig.addAIPot(ipots, this, "RegBiasBp,28,p,normal,Bias of OTA fixing the shifted source bias OffsetBn of the pre-amplifier");
		DavisConfig.addAIPot(ipots, this,
			"RefSsbxBn,30,n,normal,Set OffsetBns the shifted source bias voltage of the pre-amplifier with NBias");
		DavisConfig.addAIPot(ipots, this, "biasBuffer,34,n,normal,special buffer bias ");

		setPotArray(ipots);

		// Additional chip control bits.
		final List<SPIConfigValue> chipControlLocal = new ArrayList<>();

		chipControlLocal.add(new SPIConfigBit("Chip.SelPreAmpAvgxD", "If 1, connect PreAmpAvgxA to calibration neuron, if 0, commongate.",
			CypressFX3.FPGA_CHIPBIAS, (short) 145, false, this));
		chipControlLocal.add(new SPIConfigBit("Chip.SelBiasRefxD", "If 1, select Nbias Blk1N, if 0, VDAC VblkV2.", CypressFX3.FPGA_CHIPBIAS,
			(short) 146, true, this));
		chipControlLocal.add(new SPIConfigBit("Chip.SelSensexD", "If 0, hook refractory bias to Vdd (unselect).", CypressFX3.FPGA_CHIPBIAS,
			(short) 147, true, this));
		chipControlLocal.add(new SPIConfigBit("Chip.SelPosFbxD", "If 0, hook refractory bias to Vdd (unselect).", CypressFX3.FPGA_CHIPBIAS,
			(short) 148, true, this));
		chipControlLocal.add(new SPIConfigBit("Chip.SelHpxD", "If 0, hook refractory bias to Vdd (unselect).", CypressFX3.FPGA_CHIPBIAS,
			(short) 149, true, this));

		for (final SPIConfigValue cfgVal : chipControlLocal) {
			cfgVal.addObserver(this);
			allPreferencesList.add(cfgVal);
		}

		chipControl.addAll(chipControlLocal);

		setBatchEditOccurring(true);
		loadPreferences();
		setBatchEditOccurring(false);

		try {
			sendConfiguration(this);
		}
		catch (final HardwareInterfaceException ex) {
			Biasgen.log.log(Level.SEVERE, null, ex);
		}
	}
}
