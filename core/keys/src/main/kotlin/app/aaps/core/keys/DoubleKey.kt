package app.aaps.core.keys

enum class DoubleKey(
    override val key: String,
    override val defaultValue: Double,
    override val min: Double,
    override val max: Double,
    override val defaultedBySM: Boolean = false,
    override val calculatedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false
) : DoublePreferenceKey {

    OverviewInsulinButtonIncrement1("insulin_button_increment_1", 0.5, -5.0, 5.0, defaultedBySM = true, dependency = BooleanKey.OverviewShowInsulinButton),
    OverviewInsulinButtonIncrement2("insulin_button_increment_2", 1.0, -5.0, 5.0, defaultedBySM = true, dependency = BooleanKey.OverviewShowInsulinButton),
    OverviewInsulinButtonIncrement3("insulin_button_increment_3", 2.0, -5.0, 5.0, defaultedBySM = true, dependency = BooleanKey.OverviewShowInsulinButton),
    ActionsFillButton1("fill_button1", 0.3, 0.05, 20.0, defaultedBySM = true, hideParentScreenIfHidden = true),
    ActionsFillButton2("fill_button2", 0.0, 0.05, 20.0, defaultedBySM = true),
    ActionsFillButton3("fill_button3", 0.0, 0.05, 20.0, defaultedBySM = true),
    SafetyMaxBolus("treatmentssafety_maxbolus", 3.0, 0.1, 60.0),
    ApsMaxBasal("openapsma_max_basal", 1.0, 0.1, 25.0, defaultedBySM = true, calculatedBySM = true),
    ApsSmbMaxIob("openapsmb_max_iob", 3.0, 0.0, 70.0, defaultedBySM = true, calculatedBySM = true),
    ApsAmaMaxIob("openapsma_max_iob", 1.5, 0.0, 25.0, defaultedBySM = true, calculatedBySM = true),
    ApsMaxDailyMultiplier("openapsama_max_daily_safety_multiplier", 3.0, 1.0, 10.0, defaultedBySM = true),
    ApsMaxCurrentBasalMultiplier("openapsama_current_basal_safety_multiplier", 4.0, 1.0, 10.0, defaultedBySM = true),
    ApsAmaBolusSnoozeDivisor("bolussnooze_dia_divisor", 2.0, 1.0, 10.0, defaultedBySM = true),
    ApsAmaMin5MinCarbsImpact("openapsama_min_5m_carbimpact", 3.0, 1.0, 12.0, defaultedBySM = true),
    ApsSmbMin5MinCarbsImpact("openaps_smb_min_5m_carbimpact", 8.0, 1.0, 12.0, defaultedBySM = true),
    AbsorptionCutOff("absorption_cutoff", 6.0, 4.0, 10.0),
    AbsorptionMaxTime("absorption_maxtime", 6.0, 4.0, 10.0),
    AutosensMin("autosens_min", 0.7, 0.1, 1.0, defaultedBySM = true, hideParentScreenIfHidden = true),
    AutosensMax("autosens_max", 1.2, 0.5, 3.0, defaultedBySM = true),
    ApsAutoIsfMin("autoISF_min", 1.0, 0.3, 1.0, defaultedBySM = true),
    ApsAutoIsfMax("autoISF_max", 1.0, 1.0, 3.0, defaultedBySM = true),
    ApsAutoIsfBgAccelWeight("bgAccel_ISF_weight", 0.0, 0.0, 1.0, defaultedBySM = true),
    ApsAutoIsfBgBrakeWeight("bgBrake_ISF_weight", 0.0, 0.0, 1.0, defaultedBySM = true),
    ApsAutoIsfLowBgWeight("lower_ISFrange_weight", 0.0, 0.0, 2.0, defaultedBySM = true),
    ApsAutoIsfHighBgWeight("higher_ISFrange_weight", 0.0, 0.0, 2.0, defaultedBySM = true),
    ApsAutoIsfSmbDeliveryRatioBgRange("openapsama_smb_delivery_ratio_bg_range", 0.0, 0.0, 100.0, defaultedBySM = true),
    ApsAutoIsfPpWeight("pp_ISF_weight", 0.0, 0.0, 1.0, defaultedBySM = true),
    ApsAutoIsfDuraWeight("dura_ISF_weight", 0.0, 0.0, 3.0, defaultedBySM = true),
    ApsAutoIsfSmbDeliveryRatio("openapsama_smb_delivery_ratio", 0.5, 0.5, 1.0, defaultedBySM = true),
    ApsAutoIsfSmbDeliveryRatioMin("openapsama_smb_delivery_ratio_min", 0.5, 0.5, 1.0, defaultedBySM = true),
    ApsAutoIsfSmbDeliveryRatioMax("openapsama_smb_delivery_ratio_max", 0.5, 0.5, 1.0, defaultedBySM = true),
    ApsAutoIsfSmbMaxRangeExtension("openapsama_smb_max_range_extension", 1.0, 1.0, 5.0, defaultedBySM = true),
    EquilMaxBolus("equil_maxbolus", 10.0, 0.1, 25.0),
    OApsAIMIMaxSMB("key_openapsaimi_max_smb",1.0,0.05,15.0),
    OApsAIMIHighBGMaxSMB("key_openapsaimi_high_bg_max_smb",1.0,0.05,15.0),
    OApsAIMIweight("key_aimiweight",50.0,1.0,200.0),
    OApsAIMICHO("key_cho",50.0,1.0,150.0),
    OApsAIMITDD7("key_tdd7",40.0,1.0,150.0),
    OApsAIMIMorningFactor("key_oaps_aimi_morning_factor",50.0,1.0,150.0),
    OApsAIMIAfternoonFactor("key_oaps_aimi_afternoon_factor",50.0,1.0,150.0),
    OApsAIMIEveningFactor("key_oaps_aimi_evening_factor",50.0,1.0,150.0),
    OApsAIMIMealFactor("key_oaps_aimi_meal_factor",50.0,1.0,150.0),
    OApsAIMIFCLFactor("key_oaps_aimi_FCL_factor",50.0,1.0,150.0),
    OApsAIMIBFFactor("key_oaps_aimi_BF_factor",50.0,1.0,150.0),
    OApsAIMIBFPrebolus("key_prebolus_BF_mode",2.5,0.1, 10.0),
    OApsAIMIBFPrebolus2("key_prebolus2_BF_mode",2.0,0.1, 10.0),
    OApsAIMILunchFactor("key_oaps_aimi_lunch_factor",50.0,1.0,150.0),
    OApsAIMIDinnerFactor("key_oaps_aimi_dinner_factor",50.0,1.0,150.0),
    OApsAIMIHCFactor("key_oaps_aimi_HC_factor",50.0,1.0,150.0),
    OApsAIMISnackFactor("key_oaps_aimi_snack_factor",50.0,1.0,150.0),
    OApsAIMIHyperFactor("key_oaps_aimi_hyper_factor",60.0,1.0,150.0),
    OApsAIMIsleepFactor("key_oaps_aimi_sleep_factor",60.0,1.0,150.0),
    OApsAIMIMealPrebolus("key_prebolus_meal_mode",2.0,0.1, 10.0),
    OApsAIMIautodrivePrebolus("key_prebolus_autodrive_mode",1.0,0.1, 10.0),
    OApsAIMIcombinedDelta("key_combinedDelta_autodrive_mode",1.0,0.1, 20.0),
    OApsAIMIAutodriveDeviation("key_mindeviation_autodrive_mode",1.0,0.1, 5.0),
    OApsAIMIAutodriveAcceleration("key_Acceleration_autodrive_mode",1.0,0.1, 5.0),
    OApsAIMILunchPrebolus("key_prebolus_lunch_mode",2.5,0.1, 10.0),
    OApsAIMILunchPrebolus2("key_prebolus2_lunch_mode",2.0,0.1, 10.0),
    OApsAIMIDinnerPrebolus("key_prebolus_dinner_mode",2.5,0.1, 10.0),
    OApsAIMIDinnerPrebolus2("key_prebolus2_dinner_mode",2.0,0.1, 10.0),
    OApsAIMISnackPrebolus("key_prebolus_snack_mode",1.0,0.1, 10.0),
    OApsAIMIHighCarbPrebolus("key_prebolus_highcarb_mode",5.0,0.1, 10.0)


}