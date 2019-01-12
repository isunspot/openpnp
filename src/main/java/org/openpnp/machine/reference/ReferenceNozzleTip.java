package org.openpnp.machine.reference;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;

import org.apache.commons.io.IOUtils;
import org.opencv.core.KeyPoint;
import org.opencv.core.RotatedRect;
import org.openpnp.ConfigurationListener;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.gui.support.Wizard;
import org.openpnp.machine.reference.wizards.ReferenceNozzleTipConfigurationWizard;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.spi.base.AbstractNozzleTip;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.OpenCvUtils;
import org.openpnp.util.UiUtils;
import org.openpnp.util.VisionUtils;
import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.CvStage.Result;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

public class ReferenceNozzleTip extends AbstractNozzleTip {
    // TODO Remove after October 1, 2017.
    @Element(required = false)
    private Double changerStartSpeed = null;
    @Element(required = false)
    private Double changerMidSpeed = null;
    @Element(required = false)
    private Double changerMidSpeed2 = null;
    @Element(required = false)
    private Double changerEndSpeed = null;
    // END TODO Remove after October 1, 2017.
    
    @ElementList(required = false, entry = "id")
    private Set<String> compatiblePackageIds = new HashSet<>();

    @Attribute(required = false)
    private boolean allowIncompatiblePackages;
    
    @Attribute(required = false)
    private int pickDwellMilliseconds;

    @Attribute(required = false)
    private int placeDwellMilliseconds;

    @Element(required = false)
    private Location changerStartLocation = new Location(LengthUnit.Millimeters);

    @Element(required = false)
    private double changerStartToMidSpeed = 1D;
    
    @Element(required = false)
    private Location changerMidLocation = new Location(LengthUnit.Millimeters);
    
    @Element(required = false)
    private double changerMidToMid2Speed = 1D;
    
    @Element(required = false)
    private Location changerMidLocation2;
    
    @Element(required = false)
    private double changerMid2ToEndSpeed = 1D;
    
    @Element(required = false)
    private Location changerEndLocation = new Location(LengthUnit.Millimeters);
    
    
    @Element(required = false)
    private Calibration calibration = new Calibration();


    @Element(required = false)
    private double vacuumLevelPartOn;

    @Element(required = false)
    private double vacuumLevelPartOff;
    
    private Set<org.openpnp.model.Package> compatiblePackages = new HashSet<>();

    public ReferenceNozzleTip() {
        Configuration.get().addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationLoaded(Configuration configuration) throws Exception {
                for (String id : compatiblePackageIds) {
                    org.openpnp.model.Package pkg = configuration.getPackage(id);
                    if (pkg == null) {
                        continue;
                    }
                    compatiblePackages.add(pkg);
                }
                /*
                 * Backwards compatibility. Since this field is being added after the fact, if
                 * the field is not specified in the config then we just make a copy of the
                 * other mid location. The result is that if a user already has a changer
                 * configured they will not suddenly have a move to 0,0,0,0 which would break
                 * everything.
                 */
                if (changerMidLocation2 == null) {
                    changerMidLocation2 = changerMidLocation.derive(null, null, null, null);
                }
                /*
                 * Backwards compatibility for speed settings.
                 *  Map the old variables to new one if present in machine.xlm and null the old ones
                 *  */
                if (changerStartSpeed != null) {
                 changerStartToMidSpeed = changerStartSpeed;
                 changerStartSpeed = null;
            	}
                if (changerMidSpeed != null) {
                	changerMidToMid2Speed = changerMidSpeed;
                	changerMidSpeed = null;
                }
                if (changerMidSpeed2 !=null) {
                	changerMid2ToEndSpeed = changerMidSpeed2;
                	changerMidSpeed2 = null;
                }
                if (changerEndSpeed != null) {
                	changerEndSpeed = null;
                }
            }
        });
    }

    @Override
    public boolean canHandle(Part part) {
        boolean result =
                allowIncompatiblePackages || compatiblePackages.contains(part.getPackage());
        // Logger.debug("{}.canHandle({}) => {}", getName(), part.getId(), result);
        return result;
    }

    public Set<org.openpnp.model.Package> getCompatiblePackages() {
        return new HashSet<>(compatiblePackages);
    }

    public void setCompatiblePackages(Set<org.openpnp.model.Package> compatiblePackages) {
        this.compatiblePackages.clear();
        this.compatiblePackages.addAll(compatiblePackages);
        compatiblePackageIds.clear();
        for (org.openpnp.model.Package pkg : compatiblePackages) {
            compatiblePackageIds.add(pkg.getId());
        }
    }

    @Override
    public String toString() {
        return getName() + " " + getId();
    }

    @Override
    public Wizard getConfigurationWizard() {
        return new ReferenceNozzleTipConfigurationWizard(this);
    }

    @Override
    public String getPropertySheetHolderTitle() {
        return getClass().getSimpleName() + " " + getName();
    }

    @Override
    public PropertySheetHolder[] getChildPropertySheetHolders() {
        return null;
    }

    @Override
    public Action[] getPropertySheetHolderActions() {
        return new Action[] {unloadAction, loadAction, deleteAction};
    }

    @Override
    public PropertySheet[] getPropertySheets() {
        return new PropertySheet[] {new PropertySheetWizardAdapter(getConfigurationWizard())};
    }

    public boolean isAllowIncompatiblePackages() {
        return allowIncompatiblePackages;
    }

    public void setAllowIncompatiblePackages(boolean allowIncompatiblePackages) {
        this.allowIncompatiblePackages = allowIncompatiblePackages;
    }
    
    public int getPickDwellMilliseconds() {
        return pickDwellMilliseconds;
    }

    public void setPickDwellMilliseconds(int pickDwellMilliseconds) {
        this.pickDwellMilliseconds = pickDwellMilliseconds;
    }

    public int getPlaceDwellMilliseconds() {
        return placeDwellMilliseconds;
    }

    public void setPlaceDwellMilliseconds(int placeDwellMilliseconds) {
        this.placeDwellMilliseconds = placeDwellMilliseconds;
    }

    public Location getChangerStartLocation() {
        return changerStartLocation;
    }

    public void setChangerStartLocation(Location changerStartLocation) {
        this.changerStartLocation = changerStartLocation;
    }

    public Location getChangerMidLocation() {
        return changerMidLocation;
    }

    public void setChangerMidLocation(Location changerMidLocation) {
        this.changerMidLocation = changerMidLocation;
    }

    public Location getChangerMidLocation2() {
        return changerMidLocation2;
    }

    public void setChangerMidLocation2(Location changerMidLocation2) {
        this.changerMidLocation2 = changerMidLocation2;
    }

    public Location getChangerEndLocation() {
        return changerEndLocation;
    }

    public void setChangerEndLocation(Location changerEndLocation) {
        this.changerEndLocation = changerEndLocation;
    }
    
    public double getChangerStartToMidSpeed() {
        return changerStartToMidSpeed;
    }

    public void setChangerStartToMidSpeed(double changerStartToMidSpeed) {
        this.changerStartToMidSpeed = changerStartToMidSpeed;
    }

    public double getChangerMidToMid2Speed() {
        return changerMidToMid2Speed;
    }

    public void setChangerMidToMid2Speed(double changerMidToMid2Speed) {
        this.changerMidToMid2Speed = changerMidToMid2Speed;
    }

    public double getChangerMid2ToEndSpeed() {
        return changerMid2ToEndSpeed;
    }

    public void setChangerMid2ToEndSpeed(double changerMid2ToEndSpeed) {
        this.changerMid2ToEndSpeed = changerMid2ToEndSpeed;
    }

    private Nozzle getParentNozzle() {
        for (Head head : Configuration.get().getMachine().getHeads()) {
            for (Nozzle nozzle : head.getNozzles()) {
                for (NozzleTip nozzleTip : nozzle.getNozzleTips()) {
                    if (nozzleTip == this) {
                        return nozzle;
                    }
                }
            }
        }
        return null;
    }
	
    public double getVacuumLevelPartOn() {
        return vacuumLevelPartOn;
    }

    public void setVacuumLevelPartOn(double vacuumLevelPartOn) {
        this.vacuumLevelPartOn = vacuumLevelPartOn;
    }

    public double getVacuumLevelPartOff() {
        return vacuumLevelPartOff;
    }

    public void setVacuumLevelPartOff(double vacuumLevelPartOff) {
        this.vacuumLevelPartOff = vacuumLevelPartOff;
    }

    public Calibration getCalibration() {
        return calibration;
    }

    public Action loadAction = new AbstractAction("Load") {
        {
            putValue(SMALL_ICON, Icons.nozzleTipLoad);
            putValue(NAME, "Load");
            putValue(SHORT_DESCRIPTION, "Load the currently selected nozzle tip.");
        }

        @Override
        public void actionPerformed(final ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                getParentNozzle().loadNozzleTip(ReferenceNozzleTip.this);
            });
        }
    };

    public Action unloadAction = new AbstractAction("Unload") {
        {
            putValue(SMALL_ICON, Icons.nozzleTipUnload);
            putValue(NAME, "Unload");
            putValue(SHORT_DESCRIPTION, "Unload the currently loaded nozzle tip.");
        }

        @Override
        public void actionPerformed(final ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                getParentNozzle().unloadNozzleTip();
            });
        }
    };
    
    public Action deleteAction = new AbstractAction("Delete Nozzle Tip") {
        {
            putValue(SMALL_ICON, Icons.nozzleTipRemove);
            putValue(NAME, "Delete Nozzle Tip");
            putValue(SHORT_DESCRIPTION, "Delete the currently selected nozzle tip.");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            int ret = JOptionPane.showConfirmDialog(MainFrame.get(),
                    "Are you sure you want to delete " + getName() + "?",
                    "Delete " + getName() + "?", JOptionPane.YES_NO_OPTION);
            if (ret == JOptionPane.YES_OPTION) {
                getParentNozzle().removeNozzleTip(ReferenceNozzleTip.this);
            }
        }
    };

    @Root
    public static class Calibration {
        public static class CalibrationOffset {
            final Location offset;
            final double angle;

            public CalibrationOffset(Location offset, double angle) {
                this.offset = offset;
                this.angle = angle;
            }

            @Override
            public String toString() {
                return angle + " " + offset;
            }
        }
        
        public static class NozzleEccentricity {
            final Location runoutCenter;
            final double runoutRadius;

            public NozzleEccentricity(Location runoutCenter, double runoutRadius) {
                this.runoutCenter = runoutCenter;
                this.runoutRadius = runoutRadius;
            }

            @Override
            public String toString() {
                return runoutCenter + " " + runoutRadius;
            }
        }
        
        @Element(required = false)
        private CvPipeline pipeline = createDefaultPipeline();

        @Attribute(required = false)
        private double angleIncrement = 15;
        
        @Attribute(required = false)
        private boolean enabled;
        
        private boolean calibrating;
        
        private NozzleEccentricity nozzleEccentricity = null;

        public void calibrate(ReferenceNozzleTip nozzleTip) throws Exception {
            if (!isEnabled()) {
                return;
            }
            try {
                calibrating = true;
                
            	reset();

                Nozzle nozzle = nozzleTip.getParentNozzle();
                Camera camera = VisionUtils.getBottomVisionCamera();

                // Move to the camera with an angle of 0.
                Location cameraLocation = camera.getLocation();
                // This is our baseline location
                Location measureBaseLocation = cameraLocation.derive(null, null, null, 0d);
                
                // move nozzle to the camera location at zero degree
                MovableUtils.moveToLocationAtSafeZ(nozzle, measureBaseLocation);

                // capture nozzle tip positions and add them to a list
                List<CalibrationOffset> nozzleTipMeasuredLocations = new ArrayList<>();
                for (double i = 0; i < 360; i += angleIncrement) {
                	// Now we rotate the nozzle 360 degrees at calibration.angleIncrement steps and store the positions
                    Location measureLocation = measureBaseLocation.derive(null, null, null, i);
                    nozzle.moveTo(measureLocation);
                    Location offset = findCircle();
                    nozzleTipMeasuredLocations.add(new CalibrationOffset(offset, i));
                }
                
                // calc circle that describes the runout path
                // assumption: it's an ideal circle - must be one. really?
                /* so some refs to fit an circle function to xy-points sorted by easy to more complex:
                 * https://de.mathworks.com/matlabcentral/fileexchange/22642-circle-fit-kasa-method
                 * https://de.mathworks.com/matlabcentral/fileexchange/5557-circle-fit
                 * https://de.mathworks.com/matlabcentral/fileexchange/22643-circle-fit-pratt-method
                 * https://de.mathworks.com/matlabcentral/fileexchange/22678-circle-fit-taubin-method
                 * 
                 * starting with the implementation of kasa method
                 */
                // calc centroid of rotation (that is the rotational axis)
                double xCentroid=0;
                double yCentroid=0;
                double avgRadius=0;
                
                
                Iterator<CalibrationOffset> nozzleTipMeasuredLocationsIterator = nozzleTipMeasuredLocations.iterator();
        		while (nozzleTipMeasuredLocationsIterator.hasNext()) {
        			CalibrationOffset measuredLocation = nozzleTipMeasuredLocationsIterator.next();
        			//System.out.println(measuredLocation);
        			xCentroid += measuredLocation.offset.getX();
        			yCentroid += measuredLocation.offset.getY();
        			
        		}
        		xCentroid = xCentroid / (double)nozzleTipMeasuredLocations.size();
        		yCentroid = yCentroid / (double)nozzleTipMeasuredLocations.size();
        		
        		//calc radius of the circle
        		nozzleTipMeasuredLocationsIterator=nozzleTipMeasuredLocations.iterator();
        		while (nozzleTipMeasuredLocationsIterator.hasNext()) {
        			CalibrationOffset measuredLocation = nozzleTipMeasuredLocationsIterator.next();
        			avgRadius += Math.sqrt( Math.pow( (measuredLocation.offset.getX()-xCentroid), 2) + Math.pow( (measuredLocation.offset.getY()-yCentroid), 2));
        		}
        		avgRadius = avgRadius / (double)nozzleTipMeasuredLocations.size();
    			
                // calc the circle the runout has followed
                // now we got a representation of the runout by xyCentroid + Radius
                //result: 
                //R+center -> from that the x-y-offsets can be recalculated at any angle...
                
                
                // The nozzle tip is now calibrated and calibration.getCalibratedOffset() can be
                // used.
                this.nozzleEccentricity = new NozzleEccentricity(cameraLocation.derive(xCentroid, yCentroid, 0.0, 0.0), avgRadius);
                
                nozzle.moveToSafeZ();
            }
            finally {
                calibrating = false;
            }
        }

        public Location getCalibratedOffset(double angle) {
            if (!isEnabled() || !isCalibrated()) {
                return new Location(LengthUnit.Millimeters, 0, 0, 0, 0);
            }

            // Make sure the angle is between 0 and 360.
            while (angle < 0) {
                angle += 360;
            }
            while (angle > 360) {
                angle -= 360;
            }
            angle -= 180;
            angle = Math.toRadians(angle);
            
            /* convert from polar coords to xy cartesian offset values
             * https://blog.demofox.org/2013/10/12/converting-to-and-from-polar-spherical-coordinates-made-easy/
             */
            double offsetX = nozzleEccentricity.runoutCenter.getX() + nozzleEccentricity.runoutRadius * Math.cos(angle);
            double offsetY = nozzleEccentricity.runoutCenter.getY() + nozzleEccentricity.runoutRadius * Math.sin(angle);
        	
            return new Location(nozzleEccentricity.runoutCenter.getUnits(), offsetX, offsetY, 0, 0);
        }

        private Location findCircle() throws Exception {
            Camera camera = VisionUtils.getBottomVisionCamera();
            try (CvPipeline pipeline = getPipeline()) {
                pipeline.setProperty("camera", camera);
                pipeline.process();
                Location location;
                Object result = pipeline.getResult(VisionUtils.PIPELINE_RESULTS_NAME).model;
                if (result instanceof List) {
                    if (((List) result).get(0) instanceof Result.Circle) {
                        List<Result.Circle> circles = (List<Result.Circle>) result;
                        List<Location> locations = circles.stream().map(circle -> {
                            return VisionUtils.getPixelCenterOffsets(camera, circle.x, circle.y);
                        }).sorted((a, b) -> {
                            double a1 =
                                    a.getLinearDistanceTo(new Location(LengthUnit.Millimeters, 0, 0, 0, 0));
                            double b1 =
                                    b.getLinearDistanceTo(new Location(LengthUnit.Millimeters, 0, 0, 0, 0));
                            return Double.compare(a1, b1);
                        }).collect(Collectors.toList());
                        location = locations.get(0);
                    }
                    else if (((List) result).get(0) instanceof KeyPoint) {
                        KeyPoint keyPoint = ((List<KeyPoint>) result).get(0);
                        location = VisionUtils.getPixelCenterOffsets(camera, keyPoint.pt.x, keyPoint.pt.y);
                    }
                    else {
                        throw new Exception("Unrecognized result " + result);
                    }
                }
                else if (result instanceof RotatedRect) {
                    RotatedRect rect = (RotatedRect) result;
                    location = VisionUtils.getPixelCenterOffsets(camera, rect.center.x, rect.center.y);
                }
                else {
                    throw new Exception("Unrecognized result " + result);
                }
                MainFrame.get().get().getCameraViews().getCameraView(camera).showFilteredImage(
                        OpenCvUtils.toBufferedImage(pipeline.getWorkingImage()), 250);
                return location;
            }
        }

        public static CvPipeline createDefaultPipeline() {
            try {
                String xml = IOUtils.toString(ReferenceNozzleTip.class
                        .getResource("ReferenceNozzleTip-Calibration-DefaultPipeline.xml"));
                return new CvPipeline(xml);
            }
            catch (Exception e) {
                throw new Error(e);
            }
        }

        public void reset() {
        	nozzleEccentricity = null;
        }

        public boolean isCalibrated() {
            return nozzleEccentricity != null;
        }
        
        public boolean isCalibrating() {
            return calibrating;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public boolean isCalibrationNeeded() {
            return isEnabled() && !isCalibrated() && !isCalibrating();
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public CvPipeline getPipeline() throws Exception {
            pipeline.setProperty("camera", VisionUtils.getBottomVisionCamera());
            return pipeline;
        }

        public void setPipeline(CvPipeline calibrationPipeline) {
            this.pipeline = calibrationPipeline;
        }
    }
}
