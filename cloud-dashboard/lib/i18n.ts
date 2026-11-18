import { LanguageCode } from "@/types/telemetry";

export const TRANSLATIONS: Record<LanguageCode, Record<string, string>> = {
  EN: {
    brand_title: "AgriGuard Cloud Command Center",
    brand_subtitle: "Enterprise Protected Agriculture SCADA & Edge Supervisor",
    status_sse: "SSE LIVE (1 Hz)",
    status_polling: "HTTP POLLING (2s)",
    status_reconnecting: "RECONNECTING",
    status_preview: "SIMULATED PREVIEW",
    zone_label: "Plantation Zone",
    crop_profile: "Crop Profile",
    security_role: "Operator Role",
    unit_system: "Units",
    language: "Language",

    // Safety Banner
    safety_nominal_title: "NOMINAL CLOSED-LOOP CONTROL ACTIVE",
    safety_nominal_desc: "All temperature channels agree within consensus bounds. Hysteresis rules active.",
    safety_tripped_title: "CRITICAL SAFETY INTERLOCK ACTIVE — MANUAL OVERRIDES LOCKED",
    safety_tripped_desc: "Physical outputs forced to fail-safe state: Heater OFF, Exhaust Fan ON, Siren ON.",

    // Microclimate Cards
    temp_title: "Voted Canopy Temp",
    vpd_title: "Vapor Pressure Deficit",
    humidity_title: "Relative Humidity",
    soil_title: "Volumetric Soil Moisture",

    // Predictive Agronomy Card
    agronomy_card_title: "Predictive Agronomy & Condensation Defense",
    dew_point: "Dew Point (Td)",
    dew_spread: "Dew Point Spread (ΔT)",
    fungal_risk: "Botrytis Fungal Risk",
    gdd_accumulated: "Accumulated GDD",
    et0_rate: "Ref. ET₀",
    irrigation_advisory: "Pulse Irrigation Advisory",
    risk_low: "Low Risk",
    risk_moderate: "Moderate Risk",
    risk_high: "High Risk (Exhaust Advised)",
    risk_critical: "Critical (Condensation Imminent)",

    // Actuators
    actuators_title: "Remote Actuator Control Matrix",
    actuator_heater: "Auxiliary Heating Matrix",
    actuator_fan: "Exhaust Ventilation Fan",
    actuator_pump: "Root Zone Drip Pump",
    actuator_fogger: "High-Pressure Misting Fogger",
    actuator_alarm: "Emergency Audible Alarm",
    mode_auto: "AUTO",
    mode_manual: "MANUAL",
    state_on: "ON",
    state_off: "OFF",
    override_locked: "Safety Interlock Active: Overrides Locked",

    // TMR & Sensors
    tmr_title: "Triple Modular Redundancy (TMR) Sensor Health",
    consensus_status: "Consensus State",
    channel_t1: "Channel T1 (Canopy Core)",
    channel_t2: "Channel T2 (Canopy East)",
    channel_t3: "Channel T3 (Canopy West)",
    watchdog_title: "Watchdog Deadline Supervisor",

    // Commissioning & Audit
    commissioning_title: "Field Diagnostics & Fault Injection",
    audit_title: "Cryptographic Tamper-Evident SHA-256 Ledger",
    audit_records: "Total Verified Records",
    audit_chain: "Hash Chain Verification",
    audit_valid: "VALID & UNTAMPERED",
    audit_invalid: "TAMPER DETECTED",
    sync_backfill: "Catch-Up Backfill Sync",
    btn_sync: "Sync Missed Data",

    // Fault Buttons
    fault_bias: "Inject +15°C Bias (T2)",
    fault_disconnect: "Disconnect Sensor T3",
    fault_dual: "Dual Failure (T3+T1)",
    fault_surge: "Canopy Heat Wave (+14°C)",
    fault_freeze: "Freeze Transducer (T1)",
    fault_reset: "Reset All Channels",
  },
  SI: {
    brand_title: "ඇග්‍රිගාඩ් ක්ලවුඩ් මෙහෙයුම් මැදිරිය",
    brand_subtitle: "වාණිජ වගා පරිසර පාලන හා තත්‍ය කාලීන SCADA පද්ධතිය",
    status_sse: "සජීවී දත්ත ප්‍රවාහය (1 Hz)",
    status_polling: "පොලිං ක්‍රමය (තත්. 2)",
    status_reconnecting: "නැවත සම්බන්ධ වෙමින්...",
    status_preview: "ආදර්ශක දර්ශනය",
    zone_label: "වගා කලාපය",
    crop_profile: "බෝග වර්ගය",
    security_role: "පරිශීලක කාර්යභාරය",
    unit_system: "ඒකක",
    language: "භාෂාව",

    // Safety Banner
    safety_nominal_title: "සාමාන්‍ය ස්වයංක්‍රීය පාලනය ක්‍රියාත්මකයි",
    safety_nominal_desc: "සියලුම උෂ්ණත්ව සංවේදක එකඟතාවයෙන් ක්‍රියා කරයි. ස්වයංක්‍රීය නීති සක්‍රීයයි.",
    safety_tripped_title: "අත්‍යවශ්‍ය ආරක්ෂක අගුල ක්‍රියාත්මකයි — අතින් වෙනස් කිරීම් අවහිරයි",
    safety_tripped_desc: "පද්ධතිය ආරක්ෂිත තත්ත්වයට පත් කර ඇත: හීටරය අක්‍රියයි, වාතාශ්‍ර පංකාව ක්‍රියාත්මකයි, අනතුරු ඇඟවීම සක්‍රීයයි.",

    // Microclimate Cards
    temp_title: "සම්මුති වියන් උෂ්ණත්වය",
    vpd_title: "වාෂ්ප පීඩන ඌනතාවය (VPD)",
    humidity_title: "සාපේක්ෂ ආර්ද්‍රතාවය",
    soil_title: "පස් තෙතමනය",

    // Predictive Agronomy Card
    agronomy_card_title: "අනාගත කෘෂිකාර්මික අනාවැකි සහ දිලීර අවදානම් ආරක්ෂණය",
    dew_point: "පිනි ලක්ෂ්‍යය (Td)",
    dew_spread: "පිනි ලක්ෂ්‍ය පරතරය (ΔT)",
    fungal_risk: "දිලීර හා බෝට්‍රිටිස් අවදානම",
    gdd_accumulated: "සමුච්චිත GDD දර්ශකය",
    et0_rate: "වාෂ්පීභවන අනුපාතය (ET₀)",
    irrigation_advisory: "ජල සම්පාදන උපදේශය",
    risk_low: "අවම අවදානම",
    risk_moderate: "මධ්‍යම අවදානම",
    risk_high: "ඉහළ අවදානම (වාතාශ්‍රය අවශ්‍යයි)",
    risk_critical: "අධි අවදානම (දිය බිංදු ඝනීභවනය)",

    // Actuators
    actuators_title: "ක්ෂේත්‍ර ක්‍රියාකාරක පාලන පුවරුව",
    actuator_heater: "තාපන පද්ධතිය",
    actuator_fan: "පිටාර වාතාශ්‍ර පංකාව",
    actuator_pump: "බිංදු ජල සම්පාදන පොම්පය",
    actuator_fogger: "මීදුම් ආර්ද්‍රතාකාරකය",
    actuator_alarm: "හදිසි අනතුරු සයිරන් නළාව",
    mode_auto: "ස්වයංක්‍රීය",
    mode_manual: "අතින්",
    state_on: "ක්‍රියාත්මකයි",
    state_off: "අක්‍රියයි",
    override_locked: "ආරක්ෂක අගුල සක්‍රීයයි: පාලනය සීමිතයි",

    // TMR & Sensors
    tmr_title: "ත්‍රිත්ව සංවේදක (TMR) සෞඛ්‍ය තත්ත්වය",
    consensus_status: "සම්මුති තත්ත්වය",
    channel_t1: "සංවේදක T1 (මැද)",
    channel_t2: "සංවේදක T2 (නැගෙනහිර)",
    channel_t3: "සංවේදක T3 (බටහිර)",
    watchdog_title: "මුර බල්ලා (Watchdog) අධීක්ෂණය",

    // Commissioning & Audit
    commissioning_title: "ක්ෂේත්‍ර දෝෂ පරීක්ෂාව සහ ආදර්ශනය",
    audit_title: "SHA-256 ගුප්තකේතන ආරක්ෂිත දත්ත ලේඛනය",
    audit_records: "තහවුරු කළ වාර්තා ගණන",
    audit_chain: "හැෂ් දාම තහවුරු කිරීම",
    audit_valid: "වලංගුයි සහ සුරක්ෂිතයි",
    audit_invalid: "වෙනස් කිරීමක් හඳුනාගැනිණි!",
    sync_backfill: "මඟහැරුණු දත්ත ලබාගැනීම (Backfill)",
    btn_sync: "දත්ත සමමුහුර්ත කරන්න",

    // Fault Buttons
    fault_bias: "+15°C විෂමතාව (T2)",
    fault_disconnect: "T3 විසන්ධි කිරීම",
    fault_dual: "ද්විත්ව දෝෂය (T3+T1)",
    fault_surge: "තාප තරංගය (+14°C)",
    fault_freeze: "T1 අක්‍රිය කිරීම",
    fault_reset: "සියල්ල යථා තත්ත්වයට",
  },
  TA: {
    brand_title: "அக்ரிகார்ட் கிளவுட் கட்டளை மையம்",
    brand_subtitle: "நவீன பாதுகாக்கப்பட்ட விவசாய SCADA & எட்ஜ் கட்டுப்பாட்டு அமைப்பு",
    status_sse: "நேரலை ஸ்ட்ரீம் (1 Hz)",
    status_polling: "HTTP வாக்கெடுப்பு (2s)",
    status_reconnecting: "மீண்டும் இணைக்கிறது...",
    status_preview: "மாதிரி காட்சி",
    zone_label: "பயிர் மண்டலம்",
    crop_profile: "பயிர் சுயவிவரம்",
    security_role: "பயனர் பங்கு",
    unit_system: "அலகுகள்",
    language: "மொழி",

    // Safety Banner
    safety_nominal_title: "வழக்கமான தானியங்கி கட்டுப்பாடு இயங்குகிறது",
    safety_nominal_desc: "அனைத்து வெப்பநிலை உணரிகளின் அளவீடுகளும் சரியாக உள்ளன.",
    safety_tripped_title: "முக்கிய பாதுகாப்பு பூட்டு இயக்கப்பட்டது — கைமுறை மாற்றம் முடக்கப்பட்டது",
    safety_tripped_desc: "பாதுகாப்பு நிலை: ஹீட்டர் ஆஃப், மின்விசிறி ஆன், எச்சரிக்கை ஒலி ஆன்.",

    // Microclimate Cards
    temp_title: "ஏற்றுக்கொள்ளப்பட்ட வெப்பநிலை",
    vpd_title: "நீராவி அழுத்த பற்றாக்குறை (VPD)",
    humidity_title: "ஒப்பீட்டு ஈரப்பதம்",
    soil_title: "மண் ஈரப்பதம்",

    // Predictive Agronomy Card
    agronomy_card_title: "முன்னறிவிப்பு வேளாண்மை மற்றும் பூஞ்சை பாதுகாப்பு",
    dew_point: "பனி நிலை வெப்பநிலை (Td)",
    dew_spread: "பனி நிலை இடைவெளி (ΔT)",
    fungal_risk: "பூஞ்சை தொற்று ஆபத்து",
    gdd_accumulated: "திரட்டப்பட்ட GDD",
    et0_rate: "ஆவியாதல் விகிதம் (ET₀)",
    irrigation_advisory: "நீர்ப்பாசன ஆலோசனை",
    risk_low: "குறைந்த ஆபத்து",
    risk_moderate: "மிதமான ஆபத்து",
    risk_high: "அதிக ஆபத்து (வென்டிலேஷன் தேவை)",
    risk_critical: "மிக அதிக ஆபத்து (நீர் ஒடுக்கம்)",

    // Actuators
    actuators_title: "தொலை இயக்கி கட்டுப்பாட்டு மேட்ரிக்ஸ்",
    actuator_heater: "துணை வெப்பமூட்டும் அமைப்பு",
    actuator_fan: "வெளியேற்றும் மின்விசிறி",
    actuator_pump: "சொட்டு நீர் பாசன பம்ப்",
    actuator_fogger: "மூடுபனி ஈரப்பதமூட்டி",
    actuator_alarm: "அவசர எச்சரிக்கை சைரன்",
    mode_auto: "தானியங்கி",
    mode_manual: "கைமுறை",
    state_on: "இயக்கத்தில்",
    state_off: "நிறுத்தப்பட்டது",
    override_locked: "பாதுகாப்பு பூட்டு இயக்கத்தில் உள்ளது",

    // TMR & Sensors
    tmr_title: "மும்மை சென்சார் (TMR) ஆரோக்கிய நிலை",
    consensus_status: "ஒருமித்த நிலை",
    channel_t1: "சேனல் T1 (மையம்)",
    channel_t2: "சேனல் T2 (கிழக்கு)",
    channel_t3: "சேனல் T3 (மேற்கு)",
    watchdog_title: "கண்காணிப்பாளர் (Watchdog)",

    // Commissioning & Audit
    commissioning_title: "களக் கண்டறிதல் மற்றும் தவறு உருவகப்படுத்துதல்",
    audit_title: "SHA-256 கிரிப்டோகிராஃபிக் லெட்ஜர்",
    audit_records: "சரிபார்க்கப்பட்ட பதிவுகள்",
    audit_chain: "ஹாஷ் சங்கிலி சரிபார்ப்பு",
    audit_valid: "சரிபார்க்கப்பட்டது (மாற்றமில்லை)",
    audit_invalid: "சேதமடைந்தது கண்டறியப்பட்டது!",
    sync_backfill: "மீதமுள்ள தரவு ஒத்திசைவு (Backfill)",
    btn_sync: "ஒத்திசைக்கவும்",

    // Fault Buttons
    fault_bias: "+15°C விலகல் (T2)",
    fault_disconnect: "T3 துண்டித்தல்",
    fault_dual: "இரட்டை தோல்வி (T3+T1)",
    fault_surge: "வெப்ப அலை (+14°C)",
    fault_freeze: "T1 முடக்கம்",
    fault_reset: "அனைத்தையும் மீட்டமை",
  },
};
