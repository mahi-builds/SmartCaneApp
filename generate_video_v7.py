import os
import sys
import numpy as np
import cv2
from PIL import Image, ImageDraw, ImageFont

from moviepy import VideoFileClip, ImageClip, concatenate_videoclips, ColorClip, VideoClip, CompositeVideoClip, AudioFileClip, CompositeAudioClip

ASSETS_DIR = r"c:\Users\Manasvi Bhargava\SmartCaneApp\video_assets"
OUTPUT_FILE = r"c:\Users\Manasvi Bhargava\SmartCaneApp\video_assets\draft7_voiceover.mp4"

def get_path(filename):
    return os.path.join(ASSETS_DIR, filename)

BGM_CLIP = None
try:
    BGM_CLIP = AudioFileClip(get_path("paulyudin-presentation-music-164836.mp3"))
except Exception as e:
    print("Warning: Could not load BGM", e)

global_time = 0.0

def get_bgm(start_time, duration, volume):
    if BGM_CLIP is None: return None
    track_len = BGM_CLIP.duration
    start = start_time % track_len
    end = start + duration
    
    if end <= track_len:
        segment = BGM_CLIP.subclipped(start, end)
    else:
        from moviepy import concatenate_audioclips
        part1 = BGM_CLIP.subclipped(start, track_len)
        part2 = BGM_CLIP.subclipped(0, end - track_len)
        segment = concatenate_audioclips([part1, part2])
        
    try:
        from moviepy.audio.fx import MultiplyVolume
        segment = segment.with_effects([MultiplyVolume(volume)])
    except:
        segment = segment.with_volume_multiplied(volume)
    return segment

def add_bgm_to_clip(clip, is_video=False):
    global global_time
    duration = clip.duration
    # With a voiceover across the whole video, the BGM should be consistently low.
    vol = 0.08 if is_video else 0.12 
    
    bgm_segment = get_bgm(global_time, duration, vol)
    global_time += duration
    
    if bgm_segment is None: return clip
        
    if getattr(clip, 'audio', None) is not None and is_video:
        new_audio = CompositeAudioClip([clip.audio, bgm_segment])
        return clip.with_audio(new_audio)
    else:
        return clip.with_audio(bgm_segment)

def create_title_card(title, bullets, duration=5.0):
    img = Image.new('RGBA', (1920, 1080), (15, 23, 42, 255))
    draw = ImageDraw.Draw(img)
    try:
        font_title = ImageFont.truetype("arialbd.ttf", 80)
        font_bullet = ImageFont.truetype("arial.ttf", 45)
    except:
        font_title = ImageFont.load_default()
        font_bullet = ImageFont.load_default()
        
    if hasattr(draw, 'textbbox'):
        w_title = draw.textbbox((0,0), title, font=font_title)[2]
    else:
        w_title = draw.textsize(title, font=font_title)[0]
        
    draw.text(((1920 - w_title)/2, 250), title, font=font_title, fill=(255, 255, 255, 255))
    draw.line([(1920/2 - 150, 370), (1920/2 + 150, 370)], fill=(234, 179, 8, 255), width=6)
    
    y_offset = 480
    for bullet in bullets:
        if hasattr(draw, 'textbbox'):
            w_b = draw.textbbox((0,0), bullet, font=font_bullet)[2]
        else:
            w_b = draw.textsize(bullet, font=font_bullet)[0]
        draw.text(((1920 - w_b)/2, y_offset), bullet, font=font_bullet, fill=(200, 210, 224, 255))
        y_offset += 80
        
    clip = ImageClip(np.array(img)).with_duration(duration)
    return add_bgm_to_clip(clip, is_video=False)

def prepare_professional_image(path, duration=5.0, zoom=True):
    if not os.path.exists(path): return None
    img = Image.open(path).convert('RGB')
    base_arr = np.array(img)
    
    bg_frame = cv2.resize(base_arr, (1920, 1080))
    bg_frame = cv2.GaussianBlur(bg_frame, (151, 151), 0)
    bg_frame = (bg_frame * 0.35).astype(np.uint8)
    
    def make_frame(t):
        frame = bg_frame.copy()
        z = (1.0 + 0.08 * (t / duration)) if zoom else 1.0
        new_h = int(1080 * z)
        new_w = int(base_arr.shape[1] * (new_h / base_arr.shape[0]))
        resized = cv2.resize(base_arr, (new_w, new_h))
        y1 = (new_h - 1080) // 2
        cropped = resized[y1:y1+1080, :, :]
        
        x_offset = (1920 - new_w) // 2
        if x_offset > 0:
            frame[:, x_offset:x_offset+new_w] = cropped
        else:
            x1 = (new_w - 1920) // 2
            frame[:, :] = cropped[:, x1:x1+1920]
        return frame
        
    clip = VideoClip(make_frame, duration=duration)
    return add_bgm_to_clip(clip, is_video=False)

def prepare_professional_clip(path, start=None, end=None, speed=1.0, blur_phone=False, remove_audio=False):
    if not os.path.exists(path): return None
    try:
        clip = VideoFileClip(path)
        if start is not None and end is not None: clip = clip.subclipped(start, end)
        elif start is not None: clip = clip.subclipped(start)
        elif end is not None: clip = clip.subclipped(0, end)
            
        if speed != 1.0:
            try: 
                from moviepy.video.fx import MultiplySpeed
                clip = clip.with_effects([MultiplySpeed(speed)])
            except: pass
            
        if remove_audio:
            clip = clip.without_audio()
            
        clip = clip.resized(height=1080)
        
        if blur_phone:
            def redact(image):
                frame = image.copy()
                h, w, _ = frame.shape
                y1, y2 = int(h * 0.32), int(h * 0.37)
                x1, x2 = int(w * 0.15), int(w * 0.85)
                try:
                    roi = frame[y1:y2, x1:x2]
                    blur = cv2.GaussianBlur(roi, (81, 81), 0)
                    frame[y1:y2, x1:x2] = blur
                except:
                    frame[y1:y2, x1:x2] = (15, 23, 42)
                return frame
            if hasattr(clip, 'image_transform'): clip = clip.image_transform(redact)
            else: clip = clip.fl_image(redact)
            
        first_frame = clip.get_frame(0)
        bg_frame = cv2.resize(first_frame, (1920, 1080))
        bg_frame = cv2.GaussianBlur(bg_frame, (151, 151), 0)
        bg_frame = (bg_frame * 0.35).astype(np.uint8)
        
        bg = ImageClip(bg_frame).with_duration(clip.duration)
        comp = CompositeVideoClip([bg, clip.with_position("center")])
        comp.audio = clip.audio
        return add_bgm_to_clip(comp, is_video=True)
    except Exception as e:
        print(f"Error processing {path}: {e}")
        return None

def main():
    clips = []
    
    clips.append(create_title_card("Introducing AURA", [
        "• A revolutionary low-cost Edge-AI mobility system",
        "• Designed for the visually impaired",
        "• Enhancing independence and urban safety"
    ]))
    clips.append(prepare_professional_image(get_path("1scene2.jpeg"), duration=4.0, zoom=True))
    
    clips.append(create_title_card("Smart Hardware Integration", [
        "• Powered by the ESP32 Microcontroller",
        "• Ultrasonic sensors for forward obstacle detection",
        "• IR sensors for drop-offs and descending stairs",
        "• Water sensors to detect wet and slippery surfaces"
    ]))
    clips.append(prepare_professional_clip(get_path("scene3.mp4"), speed=1.5, remove_audio=True))
    
    clips.append(create_title_card("AURA Android Application", [
        "• Seamless low-latency Bluetooth communication",
        "• Delivers continuous Text-to-Speech (TTS) guidance",
        "• 100% offline edge processing for maximum privacy"
    ]))
    clips.append(prepare_professional_clip(get_path("scene4.mp4")))
    
    clips.append(create_title_card("Real-Time Hazard Detection", [
        "• Instantaneous auditory feedback",
        "• Detects distance and approaching obstacles",
        "• Eliminates the 'detection delay' of standard canes"
    ]))
    clips.append(prepare_professional_clip(get_path("scene5A1.mp4"), start=0, end=6))
    clips.append(prepare_professional_clip(get_path("scene5A3.mp4"), start=3.6, end=13.6))
    
    clips.append(create_title_card("AI Vision Camera System", [
        "• Smartphone-based intelligent recognition",
        "• Detects doors, stairs, and complex hazards",
        "• Extends environmental awareness beyond physical reach"
    ]))
    clips.append(prepare_professional_clip(get_path("scene5B2.mp4"), start=2.2, end=6.2))
    clips.append(prepare_professional_clip(get_path("scene5B1.mp4"), start=5, end=8))
    
    clips.append(create_title_card("OCR Text Reader", [
        "• Multilingual text scanning capabilities",
        "• Converts printed text into spoken audio",
        "• Empowers users to read signs and documents"
    ]))
    clips.append(prepare_professional_clip(get_path("scene5C.mp4"), start=3, end=14))
    clips.append(prepare_professional_clip(get_path("scene5C.mp4"), start=27, end=41))
    
    clips.append(create_title_card("Emergency Support System", [
        "• Single-tap emergency activation",
        "• Sends live GPS location via SMS",
        "• Automatically calls registered emergency contacts"
    ]))
    clips.append(prepare_professional_clip(get_path("scene5D1.mp4"), start=3.1, blur_phone=True))
    clips.append(prepare_professional_image(get_path("scene5D2.jpeg"), duration=3.0, zoom=False))
    
    clips.append(create_title_card("Empowering Independence", [
        "• Low-cost and highly scalable",
        "• No internet connection required",
        "• Built for everyone, everywhere."
    ]))
    clips.append(prepare_professional_image(get_path("scene6.jpeg"), duration=5.0, zoom=True))
    clips.append(create_title_card("AURA", [
        "Empowering independence through technology."
    ]))
    
    try:
        from moviepy.video.fx import FadeIn, FadeOut
        final_clips = [c.with_effects([FadeIn(0.4), FadeOut(0.4)]) for c in clips if c is not None]
    except Exception as e:
        print("Fade effects not applied:", e)
        final_clips = [c for c in clips if c is not None]

    final_clip = concatenate_videoclips(final_clips, method="compose")
    
    # --- ADD VOICEOVER ---
    try:
        vo_path = get_path("aura.mpeg")
        if os.path.exists(vo_path):
            vo_clip = AudioFileClip(vo_path)
            # Increase VO volume slightly if needed, but it should be fine.
            # Start VO 0.5s into the video
            vo_clip = vo_clip.with_start(0.5)
            
            # Combine current audio (video TTS + BGM) with the VO
            if final_clip.audio is not None:
                final_audio = CompositeAudioClip([final_clip.audio, vo_clip])
                final_clip = final_clip.with_audio(final_audio)
            else:
                final_clip = final_clip.with_audio(vo_clip)
            print("Successfully attached Voiceover!")
        else:
            print("Could not find aura.mpeg")
    except Exception as e:
        print("Error attaching voiceover:", e)

    final_clip.write_videofile(OUTPUT_FILE, fps=24, codec="libx264", audio_codec="aac", audio_fps=44100)
    print("Draft 7 Done!")

if __name__ == "__main__":
    main()
