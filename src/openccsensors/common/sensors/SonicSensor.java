package openccsensors.common.sensors;

import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import openccsensors.common.api.ISensor;
import openccsensors.common.api.ISensorAccess;
import openccsensors.common.api.ISensorTarget;
import openccsensors.common.api.SensorUpgradeTier;
import openccsensors.common.core.OCSLog;
import openccsensors.common.sensors.targets.SonicTarget;

public class SonicSensor implements ISensor {

	private static final int BASE_RANGE = 5;
	private static final int BASE_SEETHROUGH_RANGE = 1;
	
	@Override
	public String[] getCustomMethods(SensorUpgradeTier upgrade) {
		return null;
	}

	@Override
	public Object callCustomMethod(ISensorAccess sensor, World world, int x,
			int y, int z, int methodID, Object[] args, SensorUpgradeTier upgrade) {
		return null;
	}


	@Override
	public HashMap<String, ArrayList<ISensorTarget>> getSurroundingTargets(
			World world, int sx, int sy, int sz, SensorUpgradeTier upgrade) {
		HashMap ret = new HashMap();
		
		int range = (new Double(upgrade.getMultiplier())).intValue()
				* BASE_RANGE;
		int seethrough_range=(new Double(upgrade.getMultiplier())).intValue()
				* BASE_SEETHROUGH_RANGE;

		for (int x = -range; x <= range; x++) {
			for (int y = -range; y <= range; y++) {
				for (int z = -range; z <= range; z++) {
					
					if (!(x == 0 && y == 0 && z == 0) && world.blockExists(sx + x, sy + y, sz + z)) {
 
						try {
							int id = world.getBlockId(sx + x, sy + y, sz + z);
	
							Block block = Block.blocksList[id];
								
							int distance=Math.abs(x)+Math.abs(y)+Math.abs(z);
							boolean can_see=false;
							if (distance<=seethrough_range){
								can_see=true;
							}else{
								MovingObjectPosition hit = null;
								
								try {
									hit = world.rayTraceBlocks(
											
											Vec3.createVectorHelper(
													sx + (x == 0 ? 0.5 : (x > 0 ? 1.5 : -0.5)),
													sy + (y == 0 ? 0.5 : (y > 0 ? 1.5 : -0.5)),
													sz + (z == 0 ? 0.5 : (z > 0 ? 1.5 : -0.5))
											),
											Vec3.createVectorHelper(
													sx + x + 0.5,
													sy + y + 0.5,
													sz + z + 0.5
											)
									);
								
								}catch(Exception e) {
								}
								
								if (	hit == null ||
									  ( hit.blockX == sx + x &&
										hit.blockY == sy + y &&
										hit.blockZ == sz + z )
								) {
									
									can_see=true;
								}
							}
							if (can_see){
								ArrayList<ISensorTarget> arr = new ArrayList<ISensorTarget>();
								arr.add(new SonicTarget(block, x, y, z));
								ret.put(x + "," + y + "," + z, arr);
							}
							
						}catch(Exception e) {
						}
					}
					
				}
			}
		}
		return ret;
	}
}
