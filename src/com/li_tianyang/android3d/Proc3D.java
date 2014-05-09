package com.li_tianyang.android3d;

public class Proc3D {
	Proc3D() {
	}

	public void updateGyroData(float[] val, int accuracy, long eventTimestamp,
			long sysTimestamp) {
	}

	public void updateAccelData(float[] val, int accuracy, long eventTimestamp,
			long sysTimestamp) {
	}

	public void updateCamData(byte[] data, long sysTimestamp) {
	}

	public UIUpdateData getUIData() {
		UIUpdateData data = new UIUpdateData();
		return data;
	}
}
