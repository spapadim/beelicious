/**
 * Copyright 2009 Spiros Papadimitriou <spapadim@cs.cmu.edu>
 * 
 * This file is part of Bitlicious.
 * 
 * Bitlicious is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Bitlicious is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Bitlicious.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.bitquill.delicious;

import android.app.Activity;
import android.os.Handler;

/**
 * Handler augmented with an associated activity that can be changed.
 * Overriden {@link #handleMessage(android.os.Message)} methods must be able
 * to deal with a null {@link #getActivity()} return value.
 */
public class ActivityHandler<T extends Activity> extends Handler {
	private T mActivity;
	
	public ActivityHandler () {
		mActivity = null;
	}
	
	public ActivityHandler (T activity) {
		mActivity = activity;
	}
	
	public T getActivity () {  
		return mActivity; 
	}

	public void setActivity (T activity)  {
		mActivity = activity; 
	}
}
