package cofh.thermaldynamics.block;

import codechicken.lib.block.property.PropertyInteger;
import codechicken.lib.model.DummyBakedModel;
import codechicken.lib.model.ModelRegistryHelper;
import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.raytracer.RayTracer;
import cofh.api.block.IBlockConfigGui;
import cofh.core.init.CoreProps;
import cofh.core.network.PacketHandler;
import cofh.core.render.IBlockAppearance;
import cofh.core.render.IModelRegister;
import cofh.core.render.hitbox.ICustomHitBox;
import cofh.core.render.hitbox.RenderHitbox;
import cofh.thermaldynamics.ThermalDynamics;
import cofh.thermaldynamics.duct.Attachment;
import cofh.thermaldynamics.duct.Duct;
import cofh.thermaldynamics.duct.TDDucts;
import cofh.thermaldynamics.duct.attachments.cover.Cover;
import cofh.thermaldynamics.duct.energy.GridEnergy;
import cofh.thermaldynamics.duct.entity.EntityTransport;
import cofh.thermaldynamics.duct.entity.TransportHandler;
import cofh.thermaldynamics.duct.fluid.PacketFluid;
import cofh.thermaldynamics.duct.tiles.*;
import cofh.thermaldynamics.proxy.ProxyClient;
import cofh.thermaldynamics.render.RenderDuct;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMap;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class BlockDuct extends BlockTDBase implements IBlockAppearance, IBlockConfigGui, IModelRegister {

	public static final PropertyEnum<BlockDuct.Type> VARIANT = PropertyEnum.create("type", Type.class);

	public static final PropertyInteger META = new PropertyInteger("meta", 15);
	public static final ThreadLocal<BlockPos> IGNORE_RAY_TRACE = new ThreadLocal<>();
	public int offset;

	public BlockDuct(int offset) {

		super(Material.GLASS);

		setUnlocalizedName("duct");

		setHardness(1.0F);
		setResistance(10.0F);
		setDefaultState(getBlockState().getBaseState().withProperty(META, 0));

		this.offset = offset * 16;
	}

	@Override
	protected BlockStateContainer createBlockState() {

		return new BlockStateContainer(this, META);
	}

	@Override
	@SideOnly (Side.CLIENT)
	public void getSubBlocks(Item item, CreativeTabs tab, List<ItemStack> list) {

		for (int i = 0; i < 16; i++) {
			if (TDDucts.isValid(i + offset)) {
				list.add(TDDucts.getDuct(i + offset).itemStack.copy());
			}
		}
	}

	/* TYPE METHODS */
	@Override
	public IBlockState getStateFromMeta(int meta) {

		return getDefaultState().withProperty(META, meta);
	}

	@Override
	public int getMetaFromState(IBlockState state) {

		return state.getValue(META);
	}

	@Override
	public int damageDropped(IBlockState state) {

		return state.getValue(META);
	}

	/* ITileEntityProvider */
	@Override
	public TileEntity createNewTileEntity(World world, int metadata) {

		Duct duct = TDDucts.getType(metadata + offset);

		return duct.factory.createTileEntity(duct, world);
	}

	/* BLOCK METHODS */
	@Override
	public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> collidingBoxes, @Nullable Entity entity) {

		if (entity instanceof EntityTransport) {
			return;
		}
		float min = getSize(state);
		float max = 1 - min;

		AxisAlignedBB bb = new AxisAlignedBB(min, min, min, max, max, max);
		addCollisionBoxToList(pos, entityBox, collidingBoxes, bb);
		TileGrid theTile = (TileGrid) world.getTileEntity(pos);

		if (theTile != null) {
			for (byte i = 0; i < 6; i++) {
				Attachment attachment = theTile.getAttachment(i);
				if (attachment != null) {
					attachment.addCollisionBoxesToList(entityBox, collidingBoxes, entity);
				}
				Cover cover = theTile.getCover(i);
				if (cover != null) {
					cover.addCollisionBoxesToList(entityBox, collidingBoxes, entity);
				}
			}
			if (theTile.getVisualConnectionType(0).renderDuct) {
				bb = new AxisAlignedBB(min, 0.0F, min, max, max, max);
				addCollisionBoxToList(pos, entityBox, collidingBoxes, bb);
			}
			if (theTile.getVisualConnectionType(1).renderDuct) {
				bb = new AxisAlignedBB(min, min, min, max, 1.0F, max);
				addCollisionBoxToList(pos, entityBox, collidingBoxes, bb);
			}
			if (theTile.getVisualConnectionType(2).renderDuct) {
				bb = new AxisAlignedBB(min, min, 0.0F, max, max, max);
				addCollisionBoxToList(pos, entityBox, collidingBoxes, bb);
			}
			if (theTile.getVisualConnectionType(3).renderDuct) {
				bb = new AxisAlignedBB(min, min, min, max, max, 1.0F);
				addCollisionBoxToList(pos, entityBox, collidingBoxes, bb);
			}
			if (theTile.getVisualConnectionType(4).renderDuct) {
				bb = new AxisAlignedBB(0.0F, min, min, max, max, max);
				addCollisionBoxToList(pos, entityBox, collidingBoxes, bb);
			}
			if (theTile.getVisualConnectionType(5).renderDuct) {
				bb = new AxisAlignedBB(min, min, min, 1.0F, max, max);
				addCollisionBoxToList(pos, entityBox, collidingBoxes, bb);
			}
		}
	}

	@Override
	public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase living, ItemStack stack) {

		super.onBlockPlacedBy(world, pos, state, living, stack);

		TileEntity tile = world.getTileEntity(pos);

		if (tile instanceof TileGrid) {
			((TileGrid) tile).onPlacedBy(living, stack);
		}
	}

	@Override
	public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {

		if (side == null) {
			return false;
		}
		int s;
		if (side == EnumFacing.DOWN) {
			s = 2;
		} else if (side == EnumFacing.UP) {
			s = 5;
		} else if (side == EnumFacing.NORTH) {
			s = 3;
		} else {
			s = 4;
		}
		TileGrid theTile = (TileGrid) world.getTileEntity(pos);
		return theTile != null && theTile.getAttachment(s ^ 1) != null && theTile.getAttachment(s ^ 1).shouldRSConnect();
	}

	@Override
	public boolean hasTileEntity(IBlockState state) {

		return TDDucts.isValid(getMetaFromState(state) + offset);
	}

	@Override
	public boolean isFullCube(IBlockState state) {

		return false;
	}

	@Override
	public boolean isNormalCube(IBlockState state, IBlockAccess world, BlockPos pos) {

		return false;
	}

	@Override
	public boolean isOpaqueCube(IBlockState state) {

		return false;
	}

	@Override
	public boolean isSideSolid(IBlockState base_state, IBlockAccess world, BlockPos pos, EnumFacing side) {

		TileGrid theTile = (TileGrid) world.getTileEntity(pos);
		return (theTile != null && (theTile.getCover(side.ordinal()) != null || theTile.getAttachment(side.ordinal()) != null && theTile.getAttachment(side.ordinal()).makesSideSolid())) || super.isSideSolid(base_state, world, pos, side);
	}

	//	@Override
	//	public int getStrongPower(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
	//
	//		return 0;
	//	}

	@Override
	public int getWeakPower(IBlockState blockState, IBlockAccess world, BlockPos pos, EnumFacing side) {

		TileGrid theTile = (TileGrid) world.getTileEntity(pos);
		if (theTile != null && theTile.getAttachment(side.ordinal() ^ 1) != null) {
			return theTile.getAttachment(side.ordinal() ^ 1).getRSOutput();
		}
		return 0;
	}

	@Override
	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {

		float min = getSize(state);
		float max = 1 - min;

		return new AxisAlignedBB(min, min, min, max, max, max);
	}

	//	@Override
	//	public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, ItemStack stack) {
	//
	//		return super.getStateForPlacement(world, pos, facing, hitX, hitY, hitZ, meta, placer, stack);
	//	}

	@Override
	public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {

		if (target.subHit >= 14 && target.subHit < 20) {
			TileGrid tileEntity = (TileGrid) world.getTileEntity(pos);
			Attachment attachment = tileEntity.getAttachment(target.subHit - 14);
			if (attachment != null) {
				ItemStack pickBlock = attachment.getPickBlock();
				if (pickBlock != null) {
					return pickBlock;
				}
			}
		}
		if (target.subHit >= 20 && target.subHit < 26) {
			TileGrid tileEntity = (TileGrid) world.getTileEntity(pos);
			Cover cover = tileEntity.getCover(target.subHit - 20);
			if (cover != null) {
				ItemStack pickBlock = cover.getPickBlock();
				if (pickBlock != null) {
					return pickBlock;
				}
			}
		}
		return super.getPickBlock(state, target, world, pos, player);
	}

	@Override
	public RayTraceResult collisionRayTrace(IBlockState blockState, World world, BlockPos pos, Vec3d start, Vec3d end) {

		BlockPos ignore_pos = IGNORE_RAY_TRACE.get();
		if (ignore_pos != null && ignore_pos.equals(pos)) {
			return null;
		}

		TileGrid tile = (TileGrid) world.getTileEntity(pos);
		if (tile != null) {
			List<IndexedCuboid6> cuboids = new LinkedList<>();
			tile.addTraceableCuboids(cuboids);
			return RayTracer.rayTraceCuboidsClosest(start, end, cuboids, pos);
		}
		return null;
	}

	/* RENDERING METHODS */
	@Override
	@SideOnly (Side.CLIENT)
	public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {

		super.randomDisplayTick(state, world, pos, rand);

		TileEntity tileEntity = world.getTileEntity(pos);
		if (tileEntity instanceof TileGrid) {
			((TileGrid) tileEntity).randomDisplayTick();
		}
	}

	@Override
	@SideOnly (Side.CLIENT)
	public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {

		return true;
	}

	@Override
	@SideOnly (Side.CLIENT)
	public BlockRenderLayer getBlockLayer() {

		return BlockRenderLayer.CUTOUT;
	}

	@Override
	@SideOnly (Side.CLIENT)
	public EnumBlockRenderType getRenderType(IBlockState state) {

		return ProxyClient.renderType;
	}

	/* EVENT HANDLERS */
	@SideOnly (Side.CLIENT)
	@SubscribeEvent (priority = EventPriority.HIGH)
	public void onBlockHighlight(DrawBlockHighlightEvent event) {

		RayTraceResult target = event.getTarget();
		EntityPlayer player = event.getPlayer();
		if (target.typeOfHit == RayTraceResult.Type.BLOCK && player.worldObj.getBlockState(event.getTarget().getBlockPos()).getBlock().getUnlocalizedName().equals(getUnlocalizedName())) {
			RayTracer.retraceBlock(player.worldObj, player, target.getBlockPos());

			ICustomHitBox tile = ((ICustomHitBox) player.worldObj.getTileEntity(target.getBlockPos()));
			if (tile != null && tile.shouldRenderCustomHitBox(target.subHit, player)) {
				event.setCanceled(true);
				RenderHitbox.drawSelectionBox(player, target, event.getPartialTicks(), tile.getCustomHitBox(target.subHit, player));
			}
		}
	}

	/* IBlockAppearance */
	@Override
	public IBlockState getVisualState(IBlockAccess world, BlockPos pos, EnumFacing side) {

		TileEntity tileEntity = world.getTileEntity(pos);
		if (tileEntity instanceof TileGrid) {
			Cover cover = ((TileGrid) tileEntity).getCover(side.ordinal());
			if (cover != null) {
				return cover.state;
			}
		}
		return world.getBlockState(pos);
	}

	@Override
	public boolean supportsVisualConnections() {

		return true;
	}

	/* IBlockConfigGui */
	@Override
	public boolean openConfigGui(IBlockAccess world, BlockPos pos, EnumFacing side, EntityPlayer player) {

		TileGrid tile = (TileGrid) world.getTileEntity(pos);

		if (tile instanceof IBlockConfigGui) {
			return ((IBlockConfigGui) tile).openConfigGui(world, pos, side, player);
		} else if (tile != null) {
			int subHit = side.ordinal();
			if (world instanceof World) {
				RayTraceResult rayTrace = RayTracer.retraceBlock((World) world, player, pos);
				if (rayTrace == null) {
					return false;
				}
				if (subHit > 13 && subHit < 20) {
					subHit = rayTrace.subHit - 14;
				}
			}
			if (subHit > 13 && subHit < 20) {
				Attachment attachment = tile.getAttachment(subHit - 14);
				if (attachment instanceof IBlockConfigGui) {
					return ((IBlockConfigGui) attachment).openConfigGui(world, pos, side, player);
				}
			}
			for (DuctUnit ductUnit : tile.getDuctUnits()) {
				if (ductUnit instanceof IBlockConfigGui) {
					return ((IBlockConfigGui) ductUnit).openConfigGui(world, pos, side, player);
				}
			}
		}
		return false;
	}

	/* IModelRegister */
	@Override
	@SideOnly (Side.CLIENT)
	public void registerModels() {

		ModelLoader.setCustomStateMapper(this, new StateMap.Builder().ignore(META).build());
		ModelResourceLocation normalLocation = new ModelResourceLocation(getRegistryName(), "normal");
		ModelRegistryHelper.register(normalLocation, new DummyBakedModel());
		ModelRegistryHelper.registerItemRenderer(Item.getItemFromBlock(this), RenderDuct.instance);
	}

	/* IInitializer */
	@Override
	public boolean preInit() {

		GameRegistry.register(this.setRegistryName("ThermalDynamics_" + offset));
		GameRegistry.register(new ItemBlockDuct(this).setRegistryName("ThermalDynamics_" + offset));
		ThermalDynamics.proxy.addIModelRegister(this);

		for (int i = 0; i < 16; i++) {
			if (TDDucts.isValid(offset + i)) {
				TDDucts.getType(offset + i).itemStack = new ItemStack(this, 1, i);
			}
		}
		return true;
	}

	@Override
	public boolean initialize() {

		MinecraftForge.EVENT_BUS.register(this);

		if (offset != 0) {
			return true;
		}
		GridEnergy.initialize();

		PacketHandler.instance.registerPacket(PacketFluid.class);

		/* ENERGY */
		GameRegistry.registerTileEntity(TileDuctEnergy.Basic.class, "thermaldynamics:duct_energy_basic");
		GameRegistry.registerTileEntity(TileDuctEnergy.Hardened.class, "thermaldynamics:duct_energy_hardened");
		GameRegistry.registerTileEntity(TileDuctEnergy.Reinforced.class, "thermaldynamics:duct_energy_reinforced");
		GameRegistry.registerTileEntity(TileDuctEnergy.Signalum.class, "thermaldynamics:duct_energy_signalum");
		GameRegistry.registerTileEntity(TileDuctEnergy.Resonant.class, "thermaldynamics:duct_energy_resonant");
		GameRegistry.registerTileEntity(TileEnergyDuctSuper.class, "thermaldynamics:duct_energy_super");

		/* FLUID */
		GameRegistry.registerTileEntity(TileDuctFluid.Basic.Transparent.class, "thermaldynamics:duct_fluid_fragile_transparent");
		GameRegistry.registerTileEntity(TileDuctFluid.Basic.Opaque.class, "thermaldynamics:duct_fluid_fragile_opaque");
		GameRegistry.registerTileEntity(TileDuctFluid.Hardened.Transparent.class, "thermaldynamics:duct_fluid_hardened_transparent");
		GameRegistry.registerTileEntity(TileDuctFluid.Hardened.Opaque.class, "thermaldynamics:duct_fluid_hardened_opaque");
		GameRegistry.registerTileEntity(TileDuctFluid.Energy.Transparent.class, "thermaldynamics:duct_fluid_energy_transparent");
		GameRegistry.registerTileEntity(TileDuctFluid.Energy.Opaque.class, "thermaldynamics:duct_fluid_energy_opaque");
		GameRegistry.registerTileEntity(TileDuctFluid.Super.Transparent.class, "thermaldynamics:duct_fluid_super_transparent");
		GameRegistry.registerTileEntity(TileDuctFluid.Super.Opaque.class, "thermaldynamics:duct_fluid_super_opaque");

		GameRegistry.registerTileEntity(TileDuctItem.Basic.Transparent.class, "thermaldynamics:duct_item_transparent");
		GameRegistry.registerTileEntity(TileDuctItem.Basic.Opaque.class, "thermaldynamics:duct_item_opaque");
		GameRegistry.registerTileEntity(TileDuctItem.Fast.Transparent.class, "thermaldynamics:duct_item_fast_transparent");
		GameRegistry.registerTileEntity(TileDuctItem.Fast.Opaque.class, "thermaldynamics:duct_item_fast_opaque");
		GameRegistry.registerTileEntity(TileDuctItem.Energy.Transparent.class, "thermaldynamics:duct_item_energy_transparent");
		GameRegistry.registerTileEntity(TileDuctItem.Energy.Opaque.class, "thermaldynamics:duct_item_energy_opaque");
		GameRegistry.registerTileEntity(TileDuctItem.EnergyFast.Transparent.class, "thermaldynamics:duct_item_energy_fast_transparent");
		GameRegistry.registerTileEntity(TileDuctItem.EnergyFast.Opaque.class, "thermaldynamics:duct_item_energy_fast_opaque");

		//		GameRegistry.registerTileEntity(TileDuctItem.Warp.Transparent.class, "thermaldynamics:duct_item_warp.transparent");
		//		GameRegistry.registerTileEntity(TileDuctItem.Warp.Opaque.class, "thermaldynamics:duct_item_warp.opaque");
		//		GameRegistry.registerTileEntity(TileDuctOmni.Transparent.class, "thermaldynamics:duct_item_ender.transparent");
		//	    GameRegistry.registerTileEntity(TileDuctOmni.Opaque.class, "thermaldynamics:duct_item_ender.opaque");

		GameRegistry.registerTileEntity(TileStructuralDuct.class, "thermaldynamics:duct_structure");
		//      GameRegistry.registerTileEntity(TileDuctLight.class, "thermaldynamics:duct_structure_light");

		GameRegistry.registerTileEntity(TileTransportDuct.class, "thermaldynamics:duct_transport_basic");
		GameRegistry.registerTileEntity(TileTransportDuct.LongRange.class, "thermaldynamics:duct_transport_long_range");
		GameRegistry.registerTileEntity(TileTransportDuct.Linking.class, "thermaldynamics:duct_transport_linking");

		EntityRegistry.registerModEntity(EntityTransport.class, "transport", 0, ThermalDynamics.instance, CoreProps.ENTITY_TRACKING_DISTANCE, 1, true);
		MinecraftForge.EVENT_BUS.register(TransportHandler.INSTANCE);
		FMLCommonHandler.instance().bus().register(TransportHandler.INSTANCE);

		addRecipes();

		return true;
	}

	@Override
	public boolean postInit() {

		return true;
	}

	/* HELPERS */
	private void addRecipes() {

		// TODO
	}

	public float getSize(IBlockState state) {

		return TDDucts.getDuct(offset + getMetaFromState(state)).isLargeTube() ? 0.05F : 0.3F;
	}

	/* CONNECTIONS */
	public enum ConnectionType {

		// @formatter:off
		NONE(false),
		STRUCTURE_CLEAN,
		DUCT,
		CLEAN_DUCT,
		STRUCTURE_CONNECTION,
		TILE_CONNECTION;
		// @formatter:on

		private final boolean renderDuct;

		ConnectionType() {

			this(true);
		}

		ConnectionType(boolean renderDuct) {

			this.renderDuct = renderDuct;
		}

		public static ConnectionType getPriority(ConnectionType a, ConnectionType b) {

			if (a.ordinal() < b.ordinal()) {
				return b;
			}
			return a;
		}

		public boolean renderDuct() {

			return this.renderDuct;
		}
	}

	/* TYPE */
	public enum Type implements IStringSerializable {

		// @formatter:off
		ENERGY(0, "energy"),
		FLUID(1, "fluid"),
		FLUID_TRANS(2, "fluid_trans"),
		ITEM(3, "item"),
		ITEM_TRANS(4, "item_trans"),
		TRANSPORT(5, "transport");
		// @formatter:on

		private static final Type[] METADATA_LOOKUP = new Type[values().length];

		private final int metadata;
		private final String name;

		Type(int metadata, String name) {

			this.metadata = metadata;
			this.name = name;
		}

		public int getMetadata() {

			return this.metadata;
		}

		@Override
		public String getName() {

			return this.name;
		}

		public static Type byMetadata(int metadata) {

			if (metadata < 0 || metadata >= METADATA_LOOKUP.length) {
				metadata = 0;
			}
			return METADATA_LOOKUP[metadata];
		}

		static {
			for (Type type : values()) {
				METADATA_LOOKUP[type.getMetadata()] = type;
			}
		}
	}

	/* REFERENCES */
	public static ItemStack ductEnergy;
	public static ItemStack ductFluid;
	public static ItemStack ductItem;
	public static ItemStack ductTransport;

	public static ItemBlockDuct itemBlock;

}
