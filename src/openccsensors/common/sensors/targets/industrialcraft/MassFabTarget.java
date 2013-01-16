package openccsensors.common.sensors.targets.industrialcraft;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import openccsensors.common.api.ISensorTarget;
import openccsensors.common.sensors.targets.TileSensorTarget;

public class MassFabTarget extends TileSensorTarget implements ISensorTarget {

	public static int MASSFAB_MAX_ENERGY = 1100000;

	public MassFabTarget(TileEntity targetEntity, int relativeX, int relativeY,
			int relativeZ) {
		super(targetEntity, relativeX, relativeY, relativeZ);
	}

	@Override
	public HashMap getExtendedDetails(World world) {
		HashMap retMap = getBasicDetails(world);
		
		TileEntity machine = (TileEntity) world.getBlockTileEntity(xCoord,
				yCoord, zCoord);

		NBTTagCompound tagCompound = getTagCompound(machine);
		retMap.put("Energy", tagCompound.getInteger("energy"));
		retMap.put("MaxEnergy", MASSFAB_MAX_ENERGY);
		retMap.put("Progress", (int) ((100.0 / MASSFAB_MAX_ENERGY) * tagCompound.getInteger("energy")));
		
		return retMap;
	}

	@Override
	public String[] getTrackablePropertyNames() {
		return new String[] { "Progress" };
	}


}
