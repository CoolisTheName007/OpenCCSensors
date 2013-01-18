package openccsensors.common.sensors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.AbstractSet;
import java.lang.Math;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowing;
import net.minecraft.block.BlockStationary;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import cpw.mods.fml.common.registry.GameRegistry;

import openccsensors.OpenCCSensors;
import openccsensors.common.api.ISensorAccess;

import openccsensors.common.core.OCSLog;
import openccsensors.common.helper.SensorHelper;
import openccsensors.common.helper.TargetHelper;


import openccsensors.common.api.ISensor;
import openccsensors.common.api.ISensorAccess;
import openccsensors.common.api.ISensorTarget;
import openccsensors.common.api.ITileEntityValidatorCallback;
import openccsensors.common.api.SensorUpgradeTier;


import openccsensors.common.util.RayIterator;
import openccsensors.common.util.Vector;
import openccsensors.common.util.BlockFace;

import static openccsensors.common.util.NumberConversions.*;



public class SonicSensor implements ISensor {
	
	private static final double BASE_RANGE=5;
	
	private static double CURRENT_RANGE=BASE_RANGE;
	
	private static int DIAMETER=round(2*CURRENT_RANGE-1);
	
	private static double MAX_RES=CURRENT_RANGE/0.5d;
	//each transmission medium has a RES value; the MAX_RES/(smallest RES) is always <= MAX_RANGE
	
	
	private static final int MAX_REQUESTS= round(Math.pow(DIAMETER, 3));
	
	private static void updateRange(SensorUpgradeTier upgrade){
		CURRENT_RANGE=BASE_RANGE*upgrade.getMultiplier();
		DIAMETER=round(2*CURRENT_RANGE-1);
		MAX_RES=CURRENT_RANGE/0.5d;
	}
	
	@Override
	public String[] getCustomMethods(SensorUpgradeTier upgrade) {
		return new String[] {"scanDirection","scanSeveral","getFacing","scanCube","scanSphere","getRange","getMaxRequests"};
	}
	
	@Override
	public Object callCustomMethod(ISensorAccess sensor, World world, int x, int y, int z, int methodID, Object[] args, SensorUpgradeTier upgrade) throws Exception {
		updateRange(upgrade);
		switch(methodID)
		{
			case 0:
				return scanDirection(world,x,y,z,args);
			case 3:
				return scanCube(world,x,y,z,args);
			case 4:
				return scanSphere(world,x,y,z,args);
			case 2:
				return BlockFace.notchToBlockFace(2+sensor.getSensorEnvironment().getFacing()).toString();
			case 1:
				return scanSeveral(world,x,y,z,args);
			case 5:
				return CURRENT_RANGE;
			case 6:
				return MAX_REQUESTS;
			default:
				return null;
		}
	}

	@Override
	public HashMap<String, ArrayList<ISensorTarget>> getSurroundingTargets(
			World world, int x, int y, int z, SensorUpgradeTier upgrade) {
		updateRange(upgrade);
		HashMap<String, ArrayList<ISensorTarget>> ret = new HashMap();
		HashMap placeholder= new HashMap();
		Vector org=new Vector(x,y,z);
		for (BlockFace face : BlockFace.values()){
			if (face!=BlockFace.SELF){
				ArrayList<ISensorTarget> arr=new ArrayList();
				arr.add(
						new SonicTarget(
								world,
								org,
								new Vector(face.getModX(),face.getModY(),face.getModZ())
								)
						)
				;
				ret.put(face.toString(),arr);
			}
		}
		return ret;
	}
	
	
	
	
	
	
	public static Map scanDirection(World world, int x, int y, int z, Object[] args) throws Exception{
		if ((args.length < 3) || (!(args[0] instanceof Double)) || (!(args[1] instanceof Double)) || (!(args[2] instanceof Double)))
		{	
			
			throw new Exception("invalid arguments: must provide a non-zero vector coordinates in the form of  a varag (x,y,z,(optional args)...");
		}
		Vector org =new Vector(x, y, z);
		Vector dir =new Vector((Double) args[0],(Double) args[1] , (Double) args[2]).normalize();
		
		return new Scan(world,org,dir,(args.length>=4?(args[3] instanceof Boolean?(Boolean)args[3]:false):false),(args.length>=5?(args[4] instanceof Boolean?(Boolean)args[4]:false):false),(args.length>=6?(args[5] instanceof Boolean?(Boolean)args[5]:false):false)).serialize();
		
	}
	
	
	public static Map scanSeveral(World world, int x, int y, int z, Object[] args) throws Exception{
//		OCSLog.info("sonic scanSeveral call:"+x+','+y+','+z);
		Vector org = new Vector(x, y, z);
		Vector dir = new Vector();
		Map<Integer,Map> ret = new HashMap();
//		OCSLog.info(Integer.toString(args.length));
		if (!(args.length>=6&&(args[0] instanceof Boolean)&&(args[1] instanceof Boolean)&&(args[2] instanceof Boolean))){
			throw new Exception("invalid arguments: must provide 3 booleans representing options folllowed by sequence of 3D non-zero vector coordinates in the form of  a vararg: (hitMedium,hitEntities,rayTrace,x1,y1,z1,x2,y2,z2,x3...)");
		}
		boolean hitMedium=(Boolean) args[0];
		boolean hitEntities=(Boolean) args[1];
		boolean rayTrace=(Boolean) args[2];
		
		int i = 0;
		for (i=0;i<args.length-3;i=i+3){
//			OCSLog.info(Integer.toString(i));
			if (!(args.length-3>=2+i+1 && args[i+3] instanceof Double && args[i+1+3] instanceof Double && args[i+2+3] instanceof Double)){
				throw new Exception("invalid arguments: must provide 3 booleans representing options folllowed by sequence of 3D non-zero vector coordinates in the form of  a vararg: (hitMedium,hitEntities,rayTrace,x1,y1,z1,x2,y2,z2,x3...)");
			}
			Double x_ = (Double) args[i+3];
			Double y_ =(Double) args[i+1+3];
			Double z_ =(Double) args[i+2+3];
			
			dir.set(x_,y_,z_);
//			OCSLog.info(dir.toString());
			Scan scan = new Scan(world, org, dir,hitMedium,hitEntities,rayTrace);
			if (scan!=null){
				ret.put(i/3+1,scan.serialize());
			}
			if (i>MAX_REQUESTS){
//				OCSLog.info("b");
				//there must be a limit for the directions queried
				return null;
			}
		}
//		OCSLog.info("c");
		return ret;
	}

	
	
	
	public static Map scanCube(World world, int x, int y, int z,Object[] args){
//		OCSLog.info("sonic scan call:"+x+','+y+','+z);
		Vector org = new Vector(x, y, z);
		Vector dir = new Vector();
		Map ret = new HashMap();
		
		/*
		 * has to check all faces:
		 * up : y x,z
		 * down: -y x,z
		 * 
		 */
		
		
		dir.setX(CURRENT_RANGE);
		for (double i=-CURRENT_RANGE;i<=CURRENT_RANGE; i=i+1){
			dir.setY(i);
			for  (double j=-CURRENT_RANGE;j<=CURRENT_RANGE;j=j+1){
				dir.setZ(j);
				Scan scan = new Scan(world, org, dir);
				if (scan.result==Scan.Result.HIT){
//					OCSLog.info("result");
					ret.put(scan.vec.toString(),scan.serialize());
				}
				
			}
		}
		dir.setX(-CURRENT_RANGE);
		for (double i=-CURRENT_RANGE;i<=CURRENT_RANGE; i=i+1){
			dir.setY(i);
			for  (double j=-CURRENT_RANGE;j<=CURRENT_RANGE;j=j+1){
				dir.setZ(j);
				Scan scan = new Scan(world, org, dir);
				if (scan.result==Scan.Result.HIT){
//					OCSLog.info("result");
					ret.put(scan.vec.toString(),scan.serialize());
				}
			}
		}
		dir.setY(CURRENT_RANGE);
		for (double i=-CURRENT_RANGE;i<=CURRENT_RANGE; i=i+1){
			dir.setX(i);
			for  (double j=-CURRENT_RANGE;j<=CURRENT_RANGE;j=j+1){
				dir.setZ(j);
				Scan scan = new Scan(world, org, dir);
				if (scan.result==Scan.Result.HIT){
//					OCSLog.info("result");
					ret.put(scan.vec.toString(),scan.serialize());
				}
			}
		}
		dir.setY(-CURRENT_RANGE);
		for (double i=-CURRENT_RANGE;i<=CURRENT_RANGE; i=i+1){
			dir.setX(i);
			for  (double j=-CURRENT_RANGE;j<=CURRENT_RANGE;j=j+1){
				dir.setZ(j);
				Scan scan = new Scan(world, org, dir);
				if (scan.result==Scan.Result.HIT){
//					OCSLog.info("result");
					ret.put(scan.vec.toString(),scan.serialize());
				}
			}
		}
		dir.setZ(CURRENT_RANGE);
		for (double i=-CURRENT_RANGE;i<=CURRENT_RANGE; i=i+1){
			dir.setY(i);
			for  (double j=-CURRENT_RANGE;j<=CURRENT_RANGE;j=j+1){
				dir.setX(j);
				Scan scan = new Scan(world, org, dir);
				if (scan.result==Scan.Result.HIT){
//					OCSLog.info("result");
					ret.put(scan.vec.toString(),scan.serialize());
				}
			}
		}
		dir.setZ(-CURRENT_RANGE);
		for (double i=-CURRENT_RANGE;i<=CURRENT_RANGE; i=i+1){
			dir.setY(i);
			for  (double j=-CURRENT_RANGE;j<=CURRENT_RANGE;j=j+1){
				dir.setX(j);
				Scan scan = new Scan(world, org, dir);
				if (scan.result==Scan.Result.HIT){
//					OCSLog.info("result");
					ret.put(scan.vec.toString(),scan.serialize());
				}
			}
		}
		return ret;
	}
	
	//TODO test scanSphere against scanCube and remove getAngleStep
	
	static private double getAngleStep(double radius){
		double angle_step=Double.POSITIVE_INFINITY;
		int max = ceil(radius);
		Double[] angles = new Double[(max+1)*(max+1)];
		for (int i=0;i<=max;i=i+1){
			for (int j=0;j<=max;j=j+1){
				angles[i*(max+1)+j]=Math.atan((i+0.5)/(j+0.5));
			}
		}
		for (double angle : angles){
			for (double angle2 : angles){
				if (angle!=angle2){
					angle_step=Math.min(angle_step, Math.abs(angle-angle2));
				}
			}
		}
		return angle_step/2;
	}
	
	static private double ANGLE_STEP= getAngleStep(CURRENT_RANGE);
	
	
	
	public static Map scanSphere(World world, int x, int y, int z,Object[] args){
		OCSLog.info("sonic scanSphere call:"+x+','+y+','+z);
		double angle_step;
		if (args.length==1 && args[0] instanceof Double){
			angle_step = (Double) args[0];
		}else{
			angle_step = 2*Math.PI/100;
		}
		Vector org = new Vector(x, y, z);
		Vector dir = new Vector();
		
		Map ret = new HashMap();
		
		double H_steps=2*Math.PI/angle_step;
		double V_steps=Math.PI/angle_step;
		
		double inclination ;
		double azimuth ;
		
		int k=0;
		for (double i=0;i<=H_steps;i=i+1){
			azimuth=i/H_steps*2*Math.PI;
			for (double j=0;j<=V_steps;j=j+1){
				inclination=j/V_steps*Math.PI;
				
				dir.setFromSpherical(1, inclination, azimuth);
				
				
				Scan scan = new Scan(world, org, dir);
				if (scan.result==Scan.Result.HIT){
//					OCSLog.info("result");
					ret.put(scan.vec.toString(),scan.serialize());
				}
				
				if (++k>MAX_REQUESTS){
					return null;
				}
			}
		}
		
		return ret;
	}


	

	private static class SonicTarget extends Scan implements ISensorTarget {
		
		
		SonicTarget(World world, Vector org, Vector dir) {
			super(world, org, dir);
		}

		SonicTarget(World world, Vector org, Vector dir, boolean hitMedium,
				boolean hitEntities, boolean rayTrace) {
			super(world, org, dir, hitMedium, hitEntities, rayTrace);
		}

		private static String[] trackable={"distance"};
		@Override
		public HashMap getBasicDetails(World world) {
			return this.serialize();
		}

		@Override
		public HashMap getExtendedDetails(World world) {
			return getBasicDetails(world);
		}

		@Override
		public String[] getTrackablePropertyNames() {
			return trackable;
		}
	}
	
	private static class Scan{
		
		public enum Result{
			HIT,
			NO_REPLY,
			ZERO_DIRECTION,
			NO_RESULT,
		}
		
		public static enum Medium{
			LIQUID(0.5),
			AIR(1),
			IGNORED(1),
			SELF(1);
			double res=1;
			Medium(double res){
				this.res=res;
			}
		}
		public static enum Hit{
			LIQUID,
			AIR,
			TILE,
			ENTITY,
			CHUNK_NON_GENERATED,
			CHUNK_NON_LOADED,
			NON_RECOGNISED,
			NO_HIT;
		}
		
		
		public Result result=Result.NO_RESULT;
		public Medium medium=Medium.SELF;
		public Hit hit=Hit.NO_HIT;
		private static Vector VNaN=new Vector(Double.NaN,Double.NaN,Double.NaN);
		public Vector vec= VNaN;
		Scan(World world, Vector org, Vector dir){
			this(world, org, dir, true, true,true);
		}
		
		Scan(World world, Vector org, Vector dir,boolean hitMedium, boolean hitEntities,boolean rayTrace){
//			OCSLog.info("sonic Scan request:"+org.toString()+';'+dir.toString()+';'+Boolean.toString(hitMedium)+';'+Boolean.toString(hitEntities)+';'+Boolean.toString(rayTrace));
			if (dir.length()==0){
				this.result=Result.ZERO_DIRECTION;
				return;
			}
			
			if (!hitMedium){
				medium=Medium.IGNORED;
			}
			
			Vector start=org.clone().add(0.5,0.5,0.5);
					
			RayIterator iter= new RayIterator(start,dir);
			Vector cur=iter.next();
			
			Vec3 startVec3=start.toVec3();
			Vec3 endVec3= dir.normalize().multiply(MAX_RES*2).add(start).toVec3();
			
			Block block;
			Block bprev=Block.dirt;
			int id;
			int prev_id=0;
			
			int x;
			int y;
			int z;
			double dis;
			while (true) {
			       cur=iter.next();
			       
			       	dis=cur.distance(org);
			       	if (dis/medium.res>MAX_RES){
			    	   	this.result=Result.NO_REPLY;
//			    	   	this.vec=cur.subtract(org);
			    	   	return;
		   			}
			       x=cur.getBlockX();
			       y=cur.getBlockY();
			       z=cur.getBlockZ();
			       
			       	if (world.getChunkProvider().chunkExists(x>>4,z>>4)){
						Chunk chunk = world.getChunkFromBlockCoords(x, z);
//						for (List list : chunk.entityLists ){
////							OCSLog.info(Integer.toString(list.size()));
//							for (Object ent : list){
//								OCSLog.info(ent.toString());
////								AxisAlignedBB box = ent.getBoundingBox();
////								OCSLog.info(box.maxX+","+box.maxY+","+box.maxZ+","+box.minX+","+box.minY+","+box.minZ);
////								OCSLog.info(Integer.toString(ent.entityId));
//							}
//						}
						if (chunk.isChunkLoaded){
							id = world.getBlockId(x, y, z);
							block = Block.blocksList[id];
							
							if (id == 0 || block == null){
								if (hitMedium){
									if(medium!=Medium.SELF && medium!=Medium.AIR){
										this.hit=Hit.AIR;
										this.result=Result.HIT;
										this.vec=cur.subtract(org);
										return;
									}
									medium=Medium.AIR;
								}
								
								if(hitEntities){
									List<Entity> myList = new ArrayList<Entity>();
									chunk.getEntitiesWithinAABBForEntity(null, AxisAlignedBB.getBoundingBox(x, y, z, x+1, y+1, z+1), myList);
									
									
									for(Entity ent : myList){
										
										/*
										 *myEnt.getBoundingBox()!=myEnt.boundingbox. go figure...anyways, using the later I seem to be getting results. 
										 */
										AxisAlignedBB box = ent.boundingBox;
										
//										OCSLog.info(ent.toString());
										if (box!=null){
//											OCSLog.info(box.maxX+","+box.maxY+","+box.maxZ+","+box.minX+","+box.minY+","+box.minZ);
											
											MovingObjectPosition mov = box.calculateIntercept(startVec3, endVec3);
											if (mov!=null){
												if (rayTrace){
													this.vec=new Vector(mov.hitVec).subtract(org);
												}else{
													this.vec=new Vector(mov.hitVec).floorVector().subtract(org);
												}
												this.hit=Hit.ENTITY;
												this.result=Result.HIT;
												return;
											}
										}
										
									}
								}
							}		
							else if(block.blockMaterial.isLiquid()){
								if (hitMedium){
									if(medium!=Medium.SELF && (medium!=Medium.LIQUID ||
											((id!=prev_id)&&
													(bprev instanceof BlockStationary &&
															id!=prev_id-1)
													||
													(bprev instanceof BlockFlowing &&
															prev_id!=id-1)
											)
									)){
										this.hit=Hit.LIQUID;
										this.result=Result.HIT;
										this.vec=cur.subtract(org);
										return;
									}
									medium=Medium.LIQUID;
								}
								if(hitEntities){
									List<Entity> myList = new ArrayList<Entity>();
									chunk.getEntitiesWithinAABBForEntity(null, AxisAlignedBB.getBoundingBox(x, y, z, x+1, y+1, z+1), myList);
									
									
									for(Entity ent : myList){
										
										/*
										 *myEnt.getBoundingBox()!=myEnt.boundingbox. go figure...anyways, using the later I seem to be getting results. 
										 */
										AxisAlignedBB box = ent.boundingBox;
										
//										OCSLog.info(ent.toString());
										if (box!=null){
//											OCSLog.info(box.maxX+","+box.maxY+","+box.maxZ+","+box.minX+","+box.minY+","+box.minZ);
											
											MovingObjectPosition mov = box.calculateIntercept(startVec3, endVec3);
											if (mov!=null){
												if (rayTrace){
													this.vec=new Vector(mov.hitVec).subtract(org);
												}else{
													this.vec=new Vector(mov.hitVec).floorVector().subtract(org);
												}
												this.hit=Hit.ENTITY;
												this.result=Result.HIT;
												return;
											}
										}
										
									}
								}
							}else if (rayTrace){
									MovingObjectPosition mov = block.collisionRayTrace(world, x, y, z, startVec3,endVec3);
									if (mov!=null){
										this.vec=new Vector(mov.hitVec).subtract(org);
										this.hit=Hit.TILE;
										this.result=Result.HIT;
										return;
									}
							}else if (block.blockMaterial.isSolid()){
								this.vec=cur.subtract(org);
								this.hit=Hit.TILE;
								this.result=Result.HIT;
								return;
							}else{
								this.hit=Hit.NON_RECOGNISED;
								this.result=Result.HIT;
								this.vec=cur.subtract(org);
								return;
							}
						}else{
							this.hit=Hit.CHUNK_NON_LOADED;
							this.result=Result.HIT;
							this.vec=cur.subtract(org);
							return;
						}
					}else{
						this.hit=Hit.CHUNK_NON_GENERATED;
						this.result=Result.HIT;
						this.vec=cur.subtract(org);
						return;
					}
			       	prev_id=id;
			       	bprev=block;
			}
		}
		
		public HashMap serialize(){
			HashMap ret=new HashMap();
			ret.put("result", result.toString());
			ret.put("medium", medium.toString());
			ret.put("hit", hit.toString());
			if (vec!=VNaN){
				ret.put("vec", vec.serialize());
				ret.put("distance", vec.length());
			}
			return ret;
		}
	}
	
}





