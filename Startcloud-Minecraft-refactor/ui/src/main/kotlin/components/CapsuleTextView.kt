package cn.starcloud.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun CapsuleTag(text:String){
    Box(
        modifier =  Modifier
            .background(
                color = Color(0xFFFFFF),
                shape = RoundedCornerShape(percent = 50)
            )
        )
    {
        Text()
    }
}