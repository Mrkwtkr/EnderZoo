package crazypants.enderzoo.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import crazypants.enderzoo.config.Config;
import crazypants.enderzoo.entity.ai.EntityAIMountedArrowAttack;
import crazypants.enderzoo.entity.ai.EntityAIMountedAttackOnCollide;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIArrowAttack;
import net.minecraft.entity.ai.EntityAIBreakDoor;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

public class EntityFallenKnight extends EntitySkeleton {

  public static final int EGG_BG_COL = 0x365A25;
  public static final int EGG_FG_COL = 0x111111;
  public static String NAME = "enderzoo.FallenKnight";

  private static final double ATTACK_MOVE_SPEED = Config.fallenKnightChargeSpeed;

  private EntityAIMountedArrowAttack aiArrowAttack;
  private EntityAIMountedAttackOnCollide aiAttackOnCollide;

  private final EntityAIBreakDoor breakDoorAI = new EntityAIBreakDoor(this);
  private boolean canBreakDoors = false;

  private EntityLivingBase lastAttackTarget = null;

  private boolean firstUpdate = false;
  private boolean isMounted = false;

  public EntityFallenKnight(World world) {
    super(world);
    targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));
    targetTasks.addTask(2, new EntityAINearestAttackableTarget(this, EntityVillager.class, 0, false));
  }

  @Override
  protected void applyEntityAttributes() {
    super.applyEntityAttributes();
    getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(Config.fallenKnightFollowRange);
    getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(Config.fallenKnightBaseDamage);
  }

  @Override
  protected void attackEntity(Entity target, float distance) {
    if(attackTime <= 0 && distance < getAttackRange() && target.boundingBox.maxY > boundingBox.minY
        && target.boundingBox.minY < boundingBox.maxY) {
      attackTime = 20;
      attackEntityAsMob(target);
    }
  }

  private float getAttackRange() {
    if(isRiding()) {
      return 3;
    }
    return 2;
  }

  @Override
  public void setCombatTask() {
    tasks.removeTask(getAiAttackOnCollide());
    tasks.removeTask(getAiArrowAttack());
    ItemStack itemstack = this.getHeldItem();
    if(itemstack != null && itemstack.getItem() == Items.bow) {
      tasks.addTask(4, getAiArrowAttack());
    } else {
      tasks.addTask(4, getAiAttackOnCollide());
    }
  }

  public EntityAIMountedArrowAttack getAiArrowAttack() {
    if(aiArrowAttack == null) {
      aiArrowAttack = new EntityAIMountedArrowAttack(this, ATTACK_MOVE_SPEED, EntityFallenMount.MOUNTED_ATTACK_MOVE_SPEED,
          Config.fallenKnightRangedMinAttackPause, Config.fallenKnightRangedMaxAttackPause, Config.fallenKnightRangedMaxRange);
    }
    return aiArrowAttack;
  }

  public EntityAIMountedAttackOnCollide getAiAttackOnCollide() {
    if(aiAttackOnCollide == null) {
      aiAttackOnCollide = new EntityAIMountedAttackOnCollide(this, EntityPlayer.class, ATTACK_MOVE_SPEED, EntityFallenMount.MOUNTED_ATTACK_MOVE_SPEED, false);
    }
    return aiAttackOnCollide;
  }

  @Override
  protected String getLivingSound() {
    return "mob.zombie.say";
  }

  @Override
  protected String getHurtSound() {
    return "mob.zombie.hurt";
  }

  @Override
  protected String getDeathSound() {
    return "mob.zombie.death";
  }

  @Override
  public void onLivingUpdate() {
    super.onLivingUpdate();
    if(isRiding()) {
      EntityLiving entLiving = ((EntityLiving) ridingEntity);
      if(lastAttackTarget != getAttackTarget() || firstUpdate) {
        cancelCurrentTasks(entLiving);
        lastAttackTarget = getAttackTarget();
        firstUpdate = false;
      }
    }
    if(!isMounted == isRiding()) {
      getAiAttackOnCollide().resetTask();
      getAiArrowAttack().resetTask();
      getNavigator().clearPathEntity();
      isMounted = isRiding();
    }
    if(isBurning() && isRiding()) {
      ridingEntity.setFire(8);
    }
  }

  private void cancelCurrentTasks(EntityLiving ent) {
    Iterator iterator = ent.tasks.taskEntries.iterator();

    List<EntityAITasks.EntityAITaskEntry> currentTasks = new ArrayList<EntityAITasks.EntityAITaskEntry>();
    while (iterator.hasNext()) {
      EntityAITaskEntry entityaitaskentry = (EntityAITasks.EntityAITaskEntry) iterator.next();
      if(entityaitaskentry != null) {
        currentTasks.add(entityaitaskentry);
      }
    }
    //Only available way to stop current execution is to remove all current tasks, then re-add them 
    for (EntityAITaskEntry task : currentTasks) {
      ent.tasks.removeTask(task.action);
      ent.tasks.addTask(task.priority, task.action);
    }
  }

  @Override
  protected void addRandomArmor() {

    //Value between 0 and 1 (normal) - 1.5 based on how long a chnunk has been occupied
    float occupiedDiffcultyMultiplier = worldObj.func_147462_b(posX, posY,posZ);
    occupiedDiffcultyMultiplier /= 1.5f; // normalize

    float chanceImprovedArmor = worldObj.difficultySetting == EnumDifficulty.HARD ? Config.fallenKnightChanceArmorUpgradeHard
        : Config.fallenKnightChanceArmorUpgrade;        
    chanceImprovedArmor *= (1 + occupiedDiffcultyMultiplier); //If we have the max occupied factor, double the chance of improved armor   
    
    int armorLevel = this.rand.nextInt(2);              
    for (int i = 0; i < 2; i++) {
      if(this.rand.nextFloat() <= chanceImprovedArmor) {
        armorLevel++;
      }
    }
    if(armorLevel == 1) {
      //Skip gold armor, I don't like it
      armorLevel++;
    }     
    
    float chancePerPiece = worldObj.difficultySetting == EnumDifficulty.HARD ? Config.fallenKnightChancePerArmorPieceHard
        : Config.fallenKnightChancePerArmorPiece;

    for (int slot = 1; slot < 5; slot++) {
      ItemStack itemStack = getEquipmentInSlot(slot);
      if(itemStack == null && rand.nextFloat() <= chancePerPiece) {
        Item item = EntityLiving.getArmorItemForSlot(slot, armorLevel);
        if(item != null) {
          ItemStack stack = new ItemStack(item);
          if(armorLevel == 0) {
            ((ItemArmor)item).func_82813_b(stack, 0);
          }                    
          setCurrentItemOrArmor(slot, stack);
        }
      }
    }
    if(rand.nextFloat() > Config.fallenKnightRangedRatio) {
      setCurrentItemOrArmor(0, new ItemStack(Items.iron_sword));
    } else {
      setCurrentItemOrArmor(0, new ItemStack(Items.bow));
    }
  }

  @Override
  public IEntityLivingData onSpawnWithEgg(IEntityLivingData livingData) {

    //From base entity living class
    getEntityAttribute(SharedMonsterAttributes.followRange).applyModifier(new AttributeModifier("Random spawn bonus", this.rand.nextGaussian() * 0.05D, 1));

    setSkeletonType(0);
    addRandomArmor();
    enchantEquipment();

    EntityFallenMount mount = null;
    if(Config.fallenMountEnabled && rand.nextFloat() <= Config.fallenKnightChanceMounted) {
      mount = new EntityFallenMount(worldObj);
      mount.setLocationAndAngles(posX, posY, posZ, rotationYaw, 0.0F);
      mount.onSpawnWithEgg((IEntityLivingData) null);
      isMounted = true;
    } else {
      isMounted = false;
    }

    if(isMounted) {
      setCanPickUpLoot(false);
      setCanBreakDoors(false);
      worldObj.spawnEntityInWorld(mount);
      mountEntity(mount);
    } else {
      float f = this.worldObj.func_147462_b(this.posX, this.posY, this.posZ);
      setCanPickUpLoot(this.rand.nextFloat() < 0.55F * f);
      setCanBreakDoors(rand.nextFloat() < f * 0.1F);
    }

    return livingData;
  }

  @Override
  public void writeEntityToNBT(NBTTagCompound root) {
    super.writeEntityToNBT(root);
    root.setBoolean("canBreakDoors", canBreakDoors);
  }

  public void readEntityFromNBT(NBTTagCompound root) {
    super.readEntityFromNBT(root);
    setCanBreakDoors(root.getBoolean("canBreakDoors"));
  }

  private void setCanBreakDoors(boolean val) {
    if(canBreakDoors != val) {
      canBreakDoors = val;
      if(canBreakDoors) {
        this.tasks.addTask(1, breakDoorAI);
      } else {
        this.tasks.removeTask(breakDoorAI);
      }
    }
  }

  @Override
  protected void dropFewItems(boolean hitByPlayer, int lootingLevel) {
    int numDrops = this.rand.nextInt(3 + lootingLevel);
    for (int i = 0; i < numDrops; ++i) {
      if(rand.nextBoolean()) {
        dropItem(Items.bone, 1);
      } else {
        dropItem(Items.rotten_flesh, 1);
      }
    }
  }

  @Override
  protected void dropRareDrop(int p_70600_1_) {
  }

  //public boolean attackEntityAsMob(Entity p_70652_1_)
  //  {
  //      if (super.attackEntityAsMob(p_70652_1_))
  //      {
  //          if (this.getSkeletonType() == 1 && p_70652_1_ instanceof EntityLivingBase)
  //          {
  //              ((EntityLivingBase)p_70652_1_).addPotionEffect(new PotionEffect(Potion.wither.id, 200));
  //          }
  //
  //          return true;
  //      }
  //      else
  //      {
  //          return false;
  //      }
  //  }

  //  public boolean attackEntityAsMob(Entity p_70652_1_)
  //  {
  //      boolean flag = super.attackEntityAsMob(p_70652_1_);
  //
  //      if (flag)
  //      {
  //          int i = this.worldObj.difficultySetting.getDifficultyId();
  //
  //          if (this.getHeldItem() == null && this.isBurning() && this.rand.nextFloat() < (float)i * 0.3F)
  //          {
  //              p_70652_1_.setFire(2 * i);
  //          }
  //      }
  //
  //      return flag;
  //  }
}
