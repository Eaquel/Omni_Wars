#include <GLES3/gl3.h>
#include <EGL/egl.h>
#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <string>
#include <vector>
#include <array>
#include <random>
#include <algorithm>
#include <chrono>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "WarCore", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "WarCore", __VA_ARGS__)
#define PI 3.14159265358979f
#define DEG2RAD (PI / 180.0f)
#define MAX_PLAYERS   8
#define MAX_ENEMIES  48
#define MAX_BULLETS 256
#define MAX_PARTICLES 1024
#define MAX_LIGHTS    16
#define MAX_COVER     64

static std::mt19937 rng(std::random_device{}());
static float frand(float lo, float hi) {
    return lo + (hi - lo) * (float)rng() / (float)rng.max();
}

struct Vec2 { float x, y; };
struct Vec3 {
    float x, y, z;
    Vec3(float x=0,float y=0,float z=0): x(x),y(y),z(z){}
    Vec3 operator+(const Vec3& b) const { return {x+b.x,y+b.y,z+b.z}; }
    Vec3 operator-(const Vec3& b) const { return {x-b.x,y-b.y,z-b.z}; }
    Vec3 operator*(float s)       const { return {x*s,y*s,z*s}; }
    Vec3& operator+=(const Vec3& b){ x+=b.x; y+=b.y; z+=b.z; return *this; }
    float dot(const Vec3& b) const { return x*b.x+y*b.y+z*b.z; }
    float len()              const { return sqrtf(x*x+y*y+z*z); }
    Vec3  norm()             const { float l=len(); return l>0?(*this)*(1/l):Vec3(0,1,0); }
    Vec3  cross(const Vec3& b) const { return {y*b.z-z*b.y, z*b.x-x*b.z, x*b.y-y*b.x}; }
};
struct Vec4 { float x,y,z,w; };
struct Mat4 {
    float m[16]={0};
    static Mat4 identity() { Mat4 r; r.m[0]=r.m[5]=r.m[10]=r.m[15]=1; return r; }
};

static Mat4 mat_mul(const Mat4& a, const Mat4& b) {
    Mat4 c;
    for(int i=0;i<4;i++) for(int j=0;j<4;j++)
        for(int k=0;k<4;k++) c.m[i*4+j]+=a.m[i*4+k]*b.m[k*4+j];
    return c;
}
static Mat4 mat_translate(float tx,float ty,float tz) {
    Mat4 m=Mat4::identity();
    m.m[12]=tx; m.m[13]=ty; m.m[14]=tz; return m;
}
static Mat4 mat_scale(float sx,float sy,float sz) {
    Mat4 m=Mat4::identity();
    m.m[0]=sx; m.m[5]=sy; m.m[10]=sz; return m;
}
static Mat4 mat_rotY(float a) {
    Mat4 m=Mat4::identity();
    m.m[0]=cosf(a); m.m[2]=sinf(a); m.m[8]=-sinf(a); m.m[10]=cosf(a); return m;
}
static Mat4 mat_perspective(float fovy, float aspect, float near, float far) {
    Mat4 m;
    float f=1.0f/tanf(fovy*0.5f);
    m.m[0]=f/aspect; m.m[5]=f;
    m.m[10]=(far+near)/(near-far); m.m[11]=-1;
    m.m[14]=(2*far*near)/(near-far); return m;
}
static Mat4 mat_lookAt(Vec3 eye, Vec3 at, Vec3 up) {
    Vec3 z=(eye-at).norm();
    Vec3 x=up.cross(z).norm();
    Vec3 y=z.cross(x);
    Mat4 m=Mat4::identity();
    m.m[0]=x.x; m.m[4]=x.y; m.m[8]=x.z;
    m.m[1]=y.x; m.m[5]=y.y; m.m[9]=y.z;
    m.m[2]=z.x; m.m[6]=z.y; m.m[10]=z.z;
    m.m[12]=-x.dot(eye); m.m[13]=-y.dot(eye); m.m[14]=-z.dot(eye);
    return m;
}

static GLuint compileShader(GLenum type, const char* src) {
    GLuint s=glCreateShader(type);
    glShaderSource(s,1,&src,nullptr);
    glCompileShader(s);
    GLint ok; glGetShaderiv(s,GL_COMPILE_STATUS,&ok);
    if(!ok){ char buf[512]; glGetShaderInfoLog(s,512,nullptr,buf); LOGE("Shader: %s",buf); }
    return s;
}
static GLuint buildProgram(const char* vs, const char* fs) {
    GLuint p=glCreateProgram();
    GLuint v=compileShader(GL_VERTEX_SHADER,vs);
    GLuint f=compileShader(GL_FRAGMENT_SHADER,fs);
    glAttachShader(p,v); glAttachShader(p,f);
    glLinkProgram(p);
    GLint ok; glGetProgramiv(p,GL_LINK_STATUS,&ok);
    if(!ok){ char buf[512]; glGetProgramInfoLog(p,512,nullptr,buf); LOGE("Link: %s",buf); }
    glDeleteShader(v); glDeleteShader(f);
    return p;
}

static const char* VS_OBJECT = R"glsl(
#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec3 aColor;
uniform mat4 uMVP;
uniform mat4 uModel;
uniform float uShake;
uniform float uTime;
out vec3 vNormal;
out vec3 vFragPos;
out vec3 vColor;
void main(){
    vec4 wp = uModel * vec4(aPos,1.0);
    vFragPos = wp.xyz;
    vNormal  = mat3(transpose(inverse(uModel))) * aNormal;
    vColor   = aColor;
    vec4 cp  = uMVP * vec4(aPos,1.0);
    cp.x += sin(uTime*47.3)*uShake*0.012;
    cp.y += cos(uTime*31.7)*uShake*0.008;
    gl_Position = cp;
}
)glsl";

static const char* FS_OBJECT = R"glsl(
#version 300 es
precision mediump float;
in vec3 vNormal;
in vec3 vFragPos;
in vec3 vColor;
uniform vec3  uLightPos;
uniform vec3  uLightColor;
uniform vec3  uViewPos;
uniform float uAmbient;
uniform vec3  uPointLights[16];
uniform vec4  uPointLightColors[16];
uniform int   uNumPointLights;
out vec4 fragColor;
void main(){
    vec3 norm    = normalize(vNormal);
    vec3 ldir    = normalize(uLightPos - vFragPos);
    float diff   = max(dot(norm,ldir),0.0);
    vec3 vdir    = normalize(uViewPos - vFragPos);
    vec3 rdir    = reflect(-ldir,norm);
    float spec   = pow(max(dot(vdir,rdir),0.0),48.0);
    vec3 ambient = uAmbient * uLightColor;
    vec3 result  = (ambient + diff*uLightColor + 0.4*spec*uLightColor) * vColor;
    for(int i=0;i<uNumPointLights;i++){
        float d  = length(uPointLights[i]-vFragPos);
        float at = 1.0/(1.0+0.09*d+0.032*d*d);
        float pd = max(dot(norm,normalize(uPointLights[i]-vFragPos)),0.0);
        result  += pd * at * uPointLightColors[i].rgb * uPointLightColors[i].a * vColor;
    }
    fragColor = vec4(result,1.0);
}
)glsl";

static const char* VS_PARTICLE = R"glsl(
#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in float aSize;
layout(location=2) in vec4 aColor;
uniform mat4 uVP;
out vec4 vColor;
void main(){
    gl_Position = uVP * vec4(aPos,1.0);
    gl_PointSize = aSize * 800.0 / gl_Position.w;
    vColor = aColor;
}
)glsl";

static const char* FS_PARTICLE = R"glsl(
#version 300 es
precision mediump float;
in vec4 vColor;
out vec4 fragColor;
void main(){
    vec2 c = gl_PointCoord*2.0-1.0;
    float d = dot(c,c);
    if(d>1.0) discard;
    float a = vColor.a*(1.0-d);
    fragColor = vec4(vColor.rgb,a);
}
)glsl";

static const char* VS_TRAIL = R"glsl(
#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec4 aColor;
uniform mat4 uVP;
out vec4 vColor;
void main(){
    gl_Position = uVP * vec4(aPos,1.0);
    vColor = aColor;
}
)glsl";

static const char* FS_TRAIL = R"glsl(
#version 300 es
precision mediump float;
in vec4 vColor;
out vec4 fragColor;
void main(){ fragColor = vColor; }
)glsl";

static const char* VS_GROUND = R"glsl(
#version 300 es
layout(location=0) in vec3 aPos;
uniform mat4 uVP;
uniform float uTime;
out vec2 vUV;
out float vFog;
void main(){
    gl_Position = uVP * vec4(aPos,1.0);
    vUV  = aPos.xz;
    float d = length(aPos.xz);
    vFog = clamp((d-60.0)/200.0,0.0,1.0);
}
)glsl";

static const char* FS_GROUND = R"glsl(
#version 300 es
precision mediump float;
in vec2 vUV;
in float vFog;
uniform float uDayFactor;
out vec4 fragColor;
void main(){
    vec2 g = fract(vUV/8.0);
    float line = step(0.97,g.x)+step(0.97,g.y);
    vec3 day   = vec3(0.12,0.36,0.12);
    vec3 night = vec3(0.03,0.06,0.10);
    vec3 col   = mix(night,day,uDayFactor);
    col += line*0.06;
    vec3 fog   = mix(night,day,uDayFactor)*0.5;
    col        = mix(col,fog,vFog);
    fragColor  = vec4(col,1.0);
}
)glsl";

struct Particle {
    Vec3  pos, vel;
    Vec4  color;
    float life, maxLife, size;
    bool  alive=false;
};
struct BulletTrail {
    Vec3  pts[12];
    Vec4  colors[12];
    int   count=0;
    float life=1.0f;
    bool  alive=false;
};
struct PointLight {
    Vec3  pos;
    Vec4  color;
    float intensity;
    float life;
};
struct PlayerObj {
    Vec3  pos;
    Vec3  color;
    float health, maxHealth;
    float rotY;
    int   weapon;
    bool  alive=false;
    char  name[32]={};
};
struct EnemyObj {
    Vec3  pos, vel;
    float health, maxHealth;
    float rotY;
    bool  alive=false;
};
struct BulletObj {
    Vec3  pos, dir;
    float speed, damage, range, traveled;
    Vec4  color;
    int   weapon;
    bool  alive=false;
    float ricochets=0;
};
struct CoverObj {
    Vec3 pos, scale;
};

static GLuint gProgObj=0, gProgParticle=0, gProgTrail=0, gProgGround=0;
static GLuint gVAO_box=0, gVBO_box=0, gEBO_box=0;
static GLuint gVAO_ground=0, gVBO_ground=0;
static GLuint gVBO_particle=0, gVBO_trail=0;
static int    gWidth=1, gHeight=1;
static float  gTime=0;
static float  gShake=0;
static float  gDayFactor=1.0f;
static Vec3   gCamPos, gCamTarget;
static Mat4   gProj, gView, gVP;

static PlayerObj gPlayers[MAX_PLAYERS];
static EnemyObj  gEnemies[MAX_ENEMIES];
static BulletObj gBullets[MAX_BULLETS];
static Particle  gParticles[MAX_PARTICLES];
static BulletTrail gTrails[MAX_BULLETS];
static PointLight gLights[MAX_LIGHTS];
static CoverObj  gCovers[MAX_COVER];
static int gCoverCount=0;

static int gLocalPlayerIdx=0;
static Vec3 gPlayerInput;

static float BOX_VERTS[] = {
    -0.5f,0,-0.5f,  0,0,-1,  1,1,1,
     0.5f,0,-0.5f,  0,0,-1,  1,1,1,
     0.5f,1,-0.5f,  0,0,-1,  1,1,1,
    -0.5f,1,-0.5f,  0,0,-1,  1,1,1,
    -0.5f,0, 0.5f,  0,0, 1,  1,1,1,
     0.5f,0, 0.5f,  0,0, 1,  1,1,1,
     0.5f,1, 0.5f,  0,0, 1,  1,1,1,
    -0.5f,1, 0.5f,  0,0, 1,  1,1,1,
    -0.5f,0,-0.5f, -1,0, 0,  1,1,1,
    -0.5f,0, 0.5f, -1,0, 0,  1,1,1,
    -0.5f,1, 0.5f, -1,0, 0,  1,1,1,
    -0.5f,1,-0.5f, -1,0, 0,  1,1,1,
     0.5f,0,-0.5f,  1,0, 0,  1,1,1,
     0.5f,0, 0.5f,  1,0, 0,  1,1,1,
     0.5f,1, 0.5f,  1,0, 0,  1,1,1,
     0.5f,1,-0.5f,  1,0, 0,  1,1,1,
    -0.5f,1,-0.5f,  0,1, 0,  1,1,1,
     0.5f,1,-0.5f,  0,1, 0,  1,1,1,
     0.5f,1, 0.5f,  0,1, 0,  1,1,1,
    -0.5f,1, 0.5f,  0,1, 0,  1,1,1,
    -0.5f,0,-0.5f,  0,-1,0,  1,1,1,
     0.5f,0,-0.5f,  0,-1,0,  1,1,1,
     0.5f,0, 0.5f,  0,-1,0,  1,1,1,
    -0.5f,0, 0.5f,  0,-1,0,  1,1,1,
};
static unsigned int BOX_IDX[] = {
    0,1,2,2,3,0, 4,5,6,6,7,4, 8,9,10,10,11,8,
    12,13,14,14,15,12, 16,17,18,18,19,16, 20,21,22,22,23,20
};

static float GROUND_VERTS[] = {
    -500,0,-500, 500,0,-500, 500,0,500, -500,0,500
};

static void initGeometry() {
    glGenVertexArrays(1,&gVAO_box);
    glBindVertexArray(gVAO_box);
    glGenBuffers(1,&gVBO_box);
    glBindBuffer(GL_ARRAY_BUFFER,gVBO_box);
    glBufferData(GL_ARRAY_BUFFER,sizeof(BOX_VERTS),BOX_VERTS,GL_STATIC_DRAW);
    glGenBuffers(1,&gEBO_box);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,gEBO_box);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER,sizeof(BOX_IDX),BOX_IDX,GL_STATIC_DRAW);
    glVertexAttribPointer(0,3,GL_FLOAT,GL_FALSE,36,(void*)0);
    glVertexAttribPointer(1,3,GL_FLOAT,GL_FALSE,36,(void*)12);
    glVertexAttribPointer(2,3,GL_FLOAT,GL_FALSE,36,(void*)24);
    glEnableVertexAttribArray(0); glEnableVertexAttribArray(1); glEnableVertexAttribArray(2);

    glGenVertexArrays(1,&gVAO_ground);
    glBindVertexArray(gVAO_ground);
    glGenBuffers(1,&gVBO_ground);
    glBindBuffer(GL_ARRAY_BUFFER,gVBO_ground);
    glBufferData(GL_ARRAY_BUFFER,sizeof(GROUND_VERTS),GROUND_VERTS,GL_STATIC_DRAW);
    glVertexAttribPointer(0,3,GL_FLOAT,GL_FALSE,12,(void*)0);
    glEnableVertexAttribArray(0);

    glGenBuffers(1,&gVBO_particle);
    glGenBuffers(1,&gVBO_trail);
    glBindVertexArray(0);
}

static void spawnParticlesAt(Vec3 pos, Vec3 baseColor, int count, float speed, float life, float size) {
    int spawned=0;
    for(auto& p: gParticles) {
        if(!p.alive) {
            p.pos=pos;
            p.vel={frand(-speed,speed), frand(speed*0.5f,speed*2), frand(-speed,speed)};
            p.color={baseColor.x+frand(-0.2f,0.2f), baseColor.y+frand(-0.1f,0.1f), baseColor.z+frand(-0.2f,0.2f), 1};
            p.life=p.maxLife=life*frand(0.5f,1.5f);
            p.size=size*frand(0.5f,2.0f);
            p.alive=true;
            if(++spawned>=count) break;
        }
    }
}

static void addPointLight(Vec3 pos, Vec3 col, float intensity, float life) {
    for(auto& l: gLights) {
        if(l.life <= 0) { l={pos,{col.x,col.y,col.z,intensity},intensity,life}; return; }
    }
    gLights[0]={pos,{col.x,col.y,col.z,intensity},intensity,life};
}

static void drawBox(Vec3 pos, Vec3 scale, Vec3 color, float rotY=0) {
    glUseProgram(gProgObj);
    Mat4 T=mat_translate(pos.x,pos.y,pos.z);
    Mat4 R=mat_rotY(rotY);
    Mat4 S=mat_scale(scale.x,scale.y,scale.z);
    Mat4 model=mat_mul(mat_mul(T,R),S);
    Mat4 mvp=mat_mul(gVP,model);

    Vec3 lightPos={gCamTarget.x+50,80,gCamTarget.z+30};
    float ambient=0.15f+gDayFactor*0.15f;
    Vec3 sunColor={1.0f,0.9f+gDayFactor*0.1f,0.7f+gDayFactor*0.3f};

    glUniformMatrix4fv(glGetUniformLocation(gProgObj,"uMVP"),1,GL_FALSE,mvp.m);
    glUniformMatrix4fv(glGetUniformLocation(gProgObj,"uModel"),1,GL_FALSE,model.m);
    glUniform3f(glGetUniformLocation(gProgObj,"uLightPos"),lightPos.x,lightPos.y,lightPos.z);
    glUniform3f(glGetUniformLocation(gProgObj,"uLightColor"),sunColor.x,sunColor.y,sunColor.z);
    glUniform3f(glGetUniformLocation(gProgObj,"uViewPos"),gCamPos.x,gCamPos.y,gCamPos.z);
    glUniform1f(glGetUniformLocation(gProgObj,"uAmbient"),ambient);
    glUniform1f(glGetUniformLocation(gProgObj,"uShake"),gShake);
    glUniform1f(glGetUniformLocation(gProgObj,"uTime"),gTime);

    int numLights=0;
    float lPos[48]={}, lCol[64]={};
    for(auto& l: gLights) {
        if(l.life>0 && numLights<MAX_LIGHTS) {
            lPos[numLights*3+0]=l.pos.x; lPos[numLights*3+1]=l.pos.y; lPos[numLights*3+2]=l.pos.z;
            lCol[numLights*4+0]=l.color.x; lCol[numLights*4+1]=l.color.y;
            lCol[numLights*4+2]=l.color.z; lCol[numLights*4+3]=l.intensity;
            numLights++;
        }
    }
    glUniform3fv(glGetUniformLocation(gProgObj,"uPointLights"),numLights,lPos);
    glUniform4fv(glGetUniformLocation(gProgObj,"uPointLightColors"),numLights,lCol);
    glUniform1i(glGetUniformLocation(gProgObj,"uNumPointLights"),numLights);

    for(int i=0;i<24;i++) { BOX_VERTS[i*9+6]=color.x; BOX_VERTS[i*9+7]=color.y; BOX_VERTS[i*9+8]=color.z; }
    glBindBuffer(GL_ARRAY_BUFFER,gVBO_box);
    glBufferData(GL_ARRAY_BUFFER,sizeof(BOX_VERTS),BOX_VERTS,GL_DYNAMIC_DRAW);

    glBindVertexArray(gVAO_box);
    glDrawElements(GL_TRIANGLES,36,GL_UNSIGNED_INT,nullptr);
}

static void renderGround() {
    glUseProgram(gProgGround);
    glUniformMatrix4fv(glGetUniformLocation(gProgGround,"uVP"),1,GL_FALSE,gVP.m);
    glUniform1f(glGetUniformLocation(gProgGround,"uTime"),gTime);
    glUniform1f(glGetUniformLocation(gProgGround,"uDayFactor"),gDayFactor);
    float gv[]={
        gCamTarget.x-500,0,gCamTarget.z-500,
        gCamTarget.x+500,0,gCamTarget.z-500,
        gCamTarget.x+500,0,gCamTarget.z+500,
        gCamTarget.x-500,0,gCamTarget.z+500
    };
    glBindVertexArray(gVAO_ground);
    glBindBuffer(GL_ARRAY_BUFFER,gVBO_ground);
    glBufferData(GL_ARRAY_BUFFER,sizeof(gv),gv,GL_DYNAMIC_DRAW);
    unsigned int idx[]={0,1,2,2,3,0};
    GLuint ebo; glGenBuffers(1,&ebo);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ebo);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER,sizeof(idx),idx,GL_STATIC_DRAW);
    glDrawElements(GL_TRIANGLES,6,GL_UNSIGNED_INT,nullptr);
    glDeleteBuffers(1,&ebo);
}

static void renderPlayers() {
    for(int i=0;i<MAX_PLAYERS;i++) {
        auto& p=gPlayers[i];
        if(!p.alive) continue;
        Vec3 bodyColor=p.color;
        float hp=p.health/p.maxHealth;
        drawBox(p.pos,{0.9f,1.9f,0.9f},bodyColor,p.rotY);
        Vec3 headPos={p.pos.x,p.pos.y+2.0f,p.pos.z};
        Vec3 headColor={bodyColor.x*1.2f,bodyColor.y*1.2f,bodyColor.z*1.2f};
        drawBox(headPos,{0.7f,0.7f,0.7f},headColor,p.rotY);
        float wx=p.pos.x+sinf(p.rotY)*0.6f;
        float wz=p.pos.z+cosf(p.rotY)*0.6f;
        Vec3 weaponScale={0.12f,0.12f,0.8f+p.weapon*0.15f};
        drawBox({wx,p.pos.y+0.9f,wz},weaponScale,{0.4f,0.4f,0.4f},p.rotY);
    }
}

static void renderEnemies() {
    for(auto& e: gEnemies) {
        if(!e.alive) continue;
        drawBox(e.pos,{0.85f,1.8f,0.85f},{1.0f,0.15f,0.15f},e.rotY);
        Vec3 headPos={e.pos.x,e.pos.y+1.95f,e.pos.z};
        drawBox(headPos,{0.65f,0.65f,0.65f},{0.8f,0.1f,0.1f},e.rotY);
    }
}

static void renderCovers() {
    for(int i=0;i<gCoverCount;i++) {
        auto& c=gCovers[i];
        drawBox(c.pos,c.scale,{0.45f,0.38f,0.28f},0);
    }
}

static void renderParticles() {
    struct PV { float x,y,z,size,r,g,b,a; };
    std::vector<PV> verts;
    verts.reserve(MAX_PARTICLES);
    for(auto& p: gParticles) {
        if(!p.alive) continue;
        float t=p.life/p.maxLife;
        verts.push_back({p.pos.x,p.pos.y,p.pos.z,p.size,p.color.x,p.color.y,p.color.z,t*t});
    }
    if(verts.empty()) return;
    glUseProgram(gProgParticle);
    glUniformMatrix4fv(glGetUniformLocation(gProgParticle,"uVP"),1,GL_FALSE,gVP.m);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA,GL_ONE);
    glDepthMask(GL_FALSE);

    GLuint vao; glGenVertexArrays(1,&vao);
    glBindVertexArray(vao);
    glBindBuffer(GL_ARRAY_BUFFER,gVBO_particle);
    glBufferData(GL_ARRAY_BUFFER,verts.size()*sizeof(PV),verts.data(),GL_DYNAMIC_DRAW);
    glVertexAttribPointer(0,3,GL_FLOAT,GL_FALSE,sizeof(PV),(void*)0);
    glVertexAttribPointer(1,1,GL_FLOAT,GL_FALSE,sizeof(PV),(void*)12);
    glVertexAttribPointer(2,4,GL_FLOAT,GL_FALSE,sizeof(PV),(void*)16);
    glEnableVertexAttribArray(0); glEnableVertexAttribArray(1); glEnableVertexAttribArray(2);
    glDrawArrays(GL_POINTS,0,(int)verts.size());
    glDeleteVertexArrays(1,&vao);

    glDepthMask(GL_TRUE);
    glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
    glDisable(GL_BLEND);
}

static void renderTrails() {
    struct TV { float x,y,z,r,g,b,a; };
    std::vector<TV> verts;
    for(auto& t: gTrails) {
        if(!t.alive) continue;
        for(int i=0;i<t.count;i++) {
            float a=t.colors[i].w*t.life;
            verts.push_back({t.pts[i].x,t.pts[i].y,t.pts[i].z,
                t.colors[i].x,t.colors[i].y,t.colors[i].z,a});
        }
    }
    if(verts.empty()) return;
    glUseProgram(gProgTrail);
    glUniformMatrix4fv(glGetUniformLocation(gProgTrail,"uVP"),1,GL_FALSE,gVP.m);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA,GL_ONE);
    glDepthMask(GL_FALSE);
    glLineWidth(2.0f);

    GLuint vao; glGenVertexArrays(1,&vao);
    glBindVertexArray(vao);
    glBindBuffer(GL_ARRAY_BUFFER,gVBO_trail);
    glBufferData(GL_ARRAY_BUFFER,verts.size()*28,verts.data(),GL_DYNAMIC_DRAW);
    glVertexAttribPointer(0,3,GL_FLOAT,GL_FALSE,28,(void*)0);
    glVertexAttribPointer(1,4,GL_FLOAT,GL_FALSE,28,(void*)12);
    glEnableVertexAttribArray(0); glEnableVertexAttribArray(1);
    glDrawArrays(GL_LINE_STRIP,0,(int)verts.size());
    glDeleteVertexArrays(1,&vao);
    glDepthMask(GL_TRUE);
    glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
    glDisable(GL_BLEND);
}

static void updatePhysics(float dt) {
    gTime+=dt;
    gShake=std::max(0.0f,gShake-dt*4.0f);

    for(auto& p: gParticles) {
        if(!p.alive) continue;
        p.vel.y-=9.8f*dt;
        p.pos+=p.vel*dt;
        p.life-=dt;
        if(p.life<=0) p.alive=false;
    }
    for(auto& t: gTrails) {
        if(!t.alive) continue;
        t.life-=dt*2.0f;
        if(t.life<=0) t.alive=false;
    }
    for(auto& l: gLights) { l.life=std::max(0.0f,l.life-dt); }

    for(int i=0;i<MAX_BULLETS;i++) {
        auto& b=gBullets[i];
        if(!b.alive) continue;
        Vec3 step=b.dir*(b.speed*dt*60.0f);
        b.pos+=step;
        b.traveled+=step.len();

        if(b.traveled>0 && (int)(b.traveled/4)%(4)==0) {
            for(auto& trail: gTrails) {
                if(!trail.alive && trail.count==0) {
                    trail.pts[0]=b.pos; trail.colors[0]=b.color;
                    trail.count=1; trail.life=1.0f; trail.alive=true; break;
                }
                if(trail.alive && trail.pts[trail.count-1].x==0) {
                    if(trail.count<12) { trail.pts[trail.count]=b.pos; trail.colors[trail.count]=b.color; trail.count++; }
                    break;
                }
            }
        }

        float falloff=1.0f-std::min(1.0f,b.traveled/b.range)*0.6f;

        for(auto& e: gEnemies) {
            if(!e.alive) continue;
            float dx=e.pos.x-b.pos.x, dz=e.pos.z-b.pos.z;
            if(fabsf(dx)<0.6f && fabsf(dz)<0.6f && b.pos.y>e.pos.y && b.pos.y<e.pos.y+2.0f) {
                float dmg=b.damage*falloff;
                e.health-=dmg;
                spawnParticlesAt(b.pos,{1,0.1f,0},8,3,0.4f,0.2f);
                addPointLight(b.pos,{1,0.3f,0.1f},0.8f,0.2f);
                if(e.health<=0) {
                    e.alive=false;
                    spawnParticlesAt(e.pos+Vec3(0,1,0),{1,0.05f,0},24,5,0.8f,0.35f);
                }
                if(b.ricochets>0) {
                    b.dir.y=fabsf(b.dir.y);
                    b.dir=b.dir.norm();
                    b.ricochets--;
                } else { b.alive=false; }
                break;
            }
        }
        if(b.traveled>=b.range) b.alive=false;
    }

    Vec3 lp=gPlayers[gLocalPlayerIdx].pos;
    gCamTarget=lp;
    gCamPos={lp.x, lp.y+22, lp.z+18};
    gView=mat_lookAt(gCamPos,gCamTarget,{0,1,0});
    gVP=mat_mul(gProj,gView);
}

extern "C" {

JNIEXPORT void JNICALL Java_com_omni_wars_WarMap_nativeInit(JNIEnv*,jclass,jint w,jint h){
    gWidth=w; gHeight=h;
    glViewport(0,0,w,h);
    glEnable(GL_DEPTH_TEST);
    glEnable(GL_CULL_FACE);
    glCullFace(GL_BACK);
    glClearColor(0.05f,0.07f,0.12f,1);

    gProgObj      = buildProgram(VS_OBJECT,  FS_OBJECT);
    gProgParticle = buildProgram(VS_PARTICLE, FS_PARTICLE);
    gProgTrail    = buildProgram(VS_TRAIL,   FS_TRAIL);
    gProgGround   = buildProgram(VS_GROUND,  FS_GROUND);
    initGeometry();

    gProj=mat_perspective(60*DEG2RAD,(float)w/h,0.1f,800.0f);
    gCamPos={0,22,18}; gCamTarget={0,0,0};
    gView=mat_lookAt(gCamPos,gCamTarget,{0,1,0});
    gVP=mat_mul(gProj,gView);

    gPlayers[0].alive=true; gPlayers[0].pos={0,0,0};
    gPlayers[0].color={0.2f,0.5f,1.0f}; gPlayers[0].health=gPlayers[0].maxHealth=100;
    gPlayers[0].weapon=0;
    gLocalPlayerIdx=0;

    for(int i=0;i<MAX_COVER;i++) {
        gCovers[i]={
            {frand(-80,80),0,frand(-80,80)},
            {frand(1.5f,4),frand(1.0f,3.5f),frand(1.5f,4)}
        };
    }
    gCoverCount=MAX_COVER;

    for(int i=0;i<16;i++) {
        gEnemies[i].alive=true;
        gEnemies[i].pos={frand(-60,60),0,frand(-60,60)};
        gEnemies[i].health=gEnemies[i].maxHealth=60;
    }
    LOGD("WarCore init %dx%d",w,h);
}

JNIEXPORT void JNICALL Java_com_omni_wars_WarMap_nativeRender(JNIEnv*,jclass,jfloat dt){
    glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
    updatePhysics(dt);
    renderGround();
    renderCovers();
    renderEnemies();
    renderPlayers();
    renderTrails();
    renderParticles();
}

JNIEXPORT void JNICALL Java_com_omni_wars_WarMap_nativeSetPlayer(
    JNIEnv*,jclass,jint idx,jfloat x,jfloat y,jfloat z,
    jfloat hp,jfloat maxhp,jfloat rotY,jint weapon,jboolean alive)
{
    if(idx<0||idx>=MAX_PLAYERS) return;
    gPlayers[idx].pos={x,y,z}; gPlayers[idx].health=hp; gPlayers[idx].maxHealth=maxhp;
    gPlayers[idx].rotY=rotY; gPlayers[idx].weapon=weapon; gPlayers[idx].alive=alive;
    if(idx==gLocalPlayerIdx) { gPlayers[idx].color={0.2f,0.5f,1.0f}; }
    else                     { gPlayers[idx].color={0.2f,0.3f,0.9f}; }
}

JNIEXPORT void JNICALL Java_com_omni_wars_WarMap_nativeSetEnemy(
    JNIEnv*,jclass,jint idx,jfloat x,jfloat y,jfloat z,jfloat hp,jfloat maxhp,jboolean alive)
{
    if(idx<0||idx>=MAX_ENEMIES) return;
    gEnemies[idx].pos={x,y,z}; gEnemies[idx].health=hp;
    gEnemies[idx].maxHealth=maxhp; gEnemies[idx].alive=alive;
}

JNIEXPORT void JNICALL Java_com_omni_wars_WarMap_nativeFire(
    JNIEnv*,jclass,jfloat ox,jfloat oy,jfloat oz,
    jfloat dx,jfloat dy,jfloat dz,
    jfloat dmg,jfloat spd,jfloat range,
    jfloat cr,jfloat cg,jfloat cb,jint weapon)
{
    addPointLight({ox,oy+1,oz},{cr,cg,cb},2.0f,0.15f);
    gShake=std::min(gShake+0.3f+(weapon==3?0.6f:0),2.0f);
    for(auto& b: gBullets) {
        if(!b.alive) {
            b.pos={ox,oy+0.9f,oz}; b.dir={dx,dy,dz};
            b.speed=spd; b.damage=dmg; b.range=range;
            b.color={cr,cg,cb,1}; b.traveled=0;
            b.weapon=weapon; b.alive=true;
            b.ricochets=(weapon==2)?2.0f:0;
            break;
        }
    }
    spawnParticlesAt({ox,oy+0.9f,oz},{cr,cg,cb},6,4,0.3f,0.15f);
}

JNIEXPORT void JNICALL Java_com_omni_wars_WarMap_nativeScreenShake(JNIEnv*,jclass,jfloat intensity){
    gShake=std::min(gShake+intensity,3.0f);
}

JNIEXPORT void JNICALL Java_com_omni_wars_WarMap_nativeSpawnEffect(
    JNIEnv*,jclass,jfloat x,jfloat y,jfloat z,jfloat r,jfloat g,jfloat b,jint type)
{
    Vec3 pos={x,y,z}, col={r,g,b};
    if(type==0) spawnParticlesAt(pos,col,12,3,0.5f,0.2f);
    else if(type==1) { spawnParticlesAt(pos,col,32,8,1.0f,0.4f); addPointLight(pos,col,3,0.5f); }
    else if(type==2) { spawnParticlesAt(pos,{1,0.8f,0.2f},20,6,0.8f,0.3f); gShake+=0.5f; addPointLight(pos,{1,0.5f,0},4,0.4f); }
}

JNIEXPORT void JNICALL Java_com_omni_wars_WarMap_nativeSetDayFactor(JNIEnv*,jclass,jfloat f){
    gDayFactor=f;
}

JNIEXPORT void JNICALL Java_com_omni_wars_WarMap_nativeSetLocalPlayer(JNIEnv*,jclass,jint idx){
    gLocalPlayerIdx=idx;
}

JNIEXPORT void JNICALL Java_com_omni_wars_WarMap_nativeCleanup(JNIEnv*,jclass){
    glDeleteProgram(gProgObj); glDeleteProgram(gProgParticle);
    glDeleteProgram(gProgTrail); glDeleteProgram(gProgGround);
    glDeleteVertexArrays(1,&gVAO_box); glDeleteBuffers(1,&gVBO_box); glDeleteBuffers(1,&gEBO_box);
    glDeleteVertexArrays(1,&gVAO_ground); glDeleteBuffers(1,&gVBO_ground);
    glDeleteBuffers(1,&gVBO_particle); glDeleteBuffers(1,&gVBO_trail);
    memset(gPlayers,0,sizeof(gPlayers));
    memset(gEnemies,0,sizeof(gEnemies));
    memset(gBullets,0,sizeof(gBullets));
    memset(gParticles,0,sizeof(gParticles));
    LOGD("WarCore cleanup");
}

}
