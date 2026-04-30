import os
import sys
from moviepy import VideoFileClip, ImageClip, concatenate_videoclips, ColorClip, CompositeVideoClip
from PIL import Image, ImageDraw, ImageFont
import numpy as np

ASSETS_DIR = r"c:\Users\Manasvi Bhargava\SmartCaneApp\video_assets"
OUTPUT_FILE = r"c:\Users\Manasvi Bhargava\SmartCaneApp\video_assets\draft2.mp4"

def get_path(filename):
    return os.path.join(ASSETS_DIR, filename)

def create_text_clip(text, duration):
    try:
        font = ImageFont.truetype("arial.ttf", 80)
    except:
        font = ImageFont.load_default()

    img = Image.new('RGBA', (1920, 1080), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Quick multiline support
    lines = text.split('\n')
    y_offset = 800
    for line in lines:
        bbox = draw.textbbox((0, 0), line, font=font)
        w = bbox[2] - bbox[0]
        h = bbox[3] - bbox[1]
        x = (1920 - w) / 2
        
        # Black outline
        for dx in [-3, 0, 3]:
            for dy in [-3, 0, 3]:
                draw.text((x+dx, y_offset+dy), line, font=font, fill=(0,0,0,255))
        # White text
        draw.text((x, y_offset), line, font=font, fill=(255,255,255,255))
        y_offset += h + 10

    arr = np.array(img)
    txt_clip = ImageClip(arr).with_duration(duration)
    return txt_clip

def prepare_clip(path, start=None, end=None, duration=None, is_image=False, speed=1.0):
    if not os.path.exists(path):
        print(f"Missing: {path}")
        return None
    try:
        if is_image:
            clip = ImageClip(path).with_duration(duration if duration else 4)
            clip = clip.resized(height=1080)
            return clip
        else:
            clip = VideoFileClip(path)
            # Fix audio sync by forcing standard FPS if needed
            # Actually, trimming BEFORE resizing/speeding is better
            if start is not None and end is not None:
                clip = clip.subclipped(start, end)
            elif start is not None:
                clip = clip.subclipped(start)
            elif end is not None:
                clip = clip.subclipped(0, end)
                
            if speed != 1.0:
                clip = clip.with_effects([lambda c: c.with_speed_multiplied(speed)])
                # wait, in moviepy v2 speedx is vfx.MultiplySpeed
                # but with_speed_multiplied is available on clip? Or just speed_multiplied?
                # Actually, in v2 it's `clip.with_speed_multiplied(speed)` or `clip.multiply_speed(speed)`.
                # Let's use `clip.with_speed_multiplied(speed)`
                
            clip = clip.resized(height=1080)
            return clip
    except Exception as e:
        print(f"Error processing {path}: {e}")
        return None

def main():
    clips = []
    
    # 0. Black screen
    clips.append(ColorClip(size=(1920, 1080), color=(0, 0, 0), duration=2))

    # 1. Pic 1 (Zoom effect)
    pic1 = prepare_clip(get_path("1scene2.jpeg"), duration=4, is_image=True)
    if pic1:
        # Simple zoom effect: resize slightly larger over time
        # In moviepy v2, we can use a zoom effect or just keep it static if it's too complex.
        # Let's keep it simple for now to avoid errors, or implement a basic zoom.
        clips.append(pic1)

    # 2. Vid 1 (scene3.mp4) -> Speed up 1.5x
    vid1 = prepare_clip(get_path("scene3.mp4"))
    if vid1:
        try:
            from moviepy.video.fx import MultiplySpeed
            vid1 = vid1.with_effects([MultiplySpeed(1.5)])
        except:
            pass # fallback if effect fails
        clips.append(vid1)

    # 3. Vid 2 (scene4.mp4) -> AURA APP TEXT
    vid2 = prepare_clip(get_path("scene4.mp4"))
    if vid2:
        txt = create_text_clip("AURA Android App\nBuilt for Accessibility", vid2.duration)
        vid2_comp = CompositeVideoClip([vid2.with_position("center"), txt.with_position("center")])
        clips.append(vid2_comp)

    # 4. Vid 3 (scene5A1.mp4) -> Draft 1:01 - 1:07 (First 6 secs)
    vid3 = prepare_clip(get_path("scene5A1.mp4"), start=0, end=6)
    if vid3: clips.append(vid3)

    # Skip Vid 4 (scene5A2.mp4) as not mentioned

    # 5. Vid 5 (scene5A3.mp4) -> Draft 1:39 - 1:49 (3.6 to 13.6)
    vid5 = prepare_clip(get_path("scene5A3.mp4"), start=3.6, end=13.6)
    if vid5: clips.append(vid5)

    # 6. Vid 6 (scene5B2.mp4) -> Draft 2:04 - 2:08 (2.2 to 6.2)
    vid6 = prepare_clip(get_path("scene5B2.mp4"), start=2.2, end=6.2)
    if vid6: clips.append(vid6)

    # 7. Vid 9 (scene5B1.mp4) -> to come after Vid 6, from 5 to 8 secs
    vid9 = prepare_clip(get_path("scene5B1.mp4"), start=5, end=8)
    if vid9: clips.append(vid9)

    # 8. Vid 7 (scene5C.mp4) -> Actual timing 3-14 and 27-41
    vid7_pt1 = prepare_clip(get_path("scene5C.mp4"), start=3, end=14)
    vid7_pt2 = prepare_clip(get_path("scene5C.mp4"), start=27, end=41)
    if vid7_pt1: clips.append(vid7_pt1)
    if vid7_pt2: clips.append(vid7_pt2)

    # 9. Vid 8 (scene5D1.mp4) -> Draft 3:10 to end (3.1 to end)
    vid8 = prepare_clip(get_path("scene5D1.mp4"), start=3.1)
    if vid8: clips.append(vid8)

    # Keep scene 6 and end card
    vid_end1 = prepare_clip(get_path("scene5D2.jpeg"), duration=3, is_image=True)
    vid_end2 = prepare_clip(get_path("scene6.jpeg"), duration=5, is_image=True)
    if vid_end1: clips.append(vid_end1)
    if vid_end2: clips.append(vid_end2)

    if not clips:
        print("No clips found.")
        return

    print("Concatenating clips...")
    final_clip = concatenate_videoclips(clips, method="compose")
    
    print("Writing video output...")
    # audio_fps=44100 helps with some sync issues
    final_clip.write_videofile(OUTPUT_FILE, fps=24, codec="libx264", audio_codec="aac", audio_fps=44100)
    print("Draft 2 Done!")

if __name__ == "__main__":
    main()
