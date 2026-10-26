/* MirageLab V10 native Windows x64 entrypoint. No external runtime or CRT.
 * Does not manipulate the game; launches the portable JavaAgent wrapper.
 */
typedef unsigned long DWORD;
typedef unsigned long long UINTPTR;
typedef unsigned short WCHAR;
typedef void *PTR;
#define DLL __declspec(dllimport)
DLL DWORD __stdcall GetModuleFileNameW(PTR,WCHAR*,DWORD);
DLL DWORD __stdcall GetFileAttributesW(const WCHAR*);
DLL PTR __stdcall ShellExecuteW(PTR,const WCHAR*,const WCHAR*,const WCHAR*,const WCHAR*,int);
DLL int __stdcall MessageBoxW(PTR,const WCHAR*,const WCHAR*,unsigned int);
DLL void __stdcall ExitProcess(unsigned int);
static const WCHAR OPEN[]={'o','p','e','n',0};
static const WCHAR WSCRIPT[]={'w','s','c','r','i','p','t','.','e','x','e',0};
static const WCHAR RUN[]={'M','i','r','a','g','e','L','a','b','_','H','e','l','p','e','r','.','v','b','s',0};
static const WCHAR TITLE[]={'M','i','r','a','g','e','L','a','b',' ','V','1','0',' ','F','i','n','a','l',0};
static const WCHAR ERROR_MSG[]={'E','x','t','r','a','i','a',' ','o',' ','Z','I','P',' ','c','o','m','p','l','e','t','o',' ','a','n','t','e','s',' ','d','e',' ','e','x','e','c','u','t','a','r',' ','o',' ','M','i','r','a','g','e','L','a','b','.','e','x','e','.',' ','V','e','r','i','f','i','q','u','e',' ','o','s',' ','a','r','q','u','i','v','o','s',' ','.','v','b','s',' ','e',' ','o',' ','J','A','R','.',0};
static void copystr(WCHAR *dst,const WCHAR *src,unsigned int max){unsigned int j=0;while(src[j]&&j+1<max){dst[j]=src[j];j++;}dst[j]=0;}
static void append(WCHAR*dst,const WCHAR*src,unsigned int max){unsigned int n=0,k=0;while(dst[n]&&n<max)n++;while(src[k]&&n+k+1<max){dst[n+k]=src[k];k++;}if(n+k<max)dst[n+k]=0;}
static int exists(const WCHAR *p){DWORD a=GetFileAttributesW(p);return a!=(DWORD)-1;}
static WCHAR base[2048],script[2048],param[2052];
void mainCRTStartup(void){
 DWORD n=GetModuleFileNameW((PTR)0,base,2048);
 if(n==0||n>=2047){MessageBoxW((PTR)0,ERROR_MSG,TITLE,0x10);ExitProcess(2);}
 unsigned int end=n;while(end&&base[end-1]!='\\')end--;
 if(end==0){MessageBoxW((PTR)0,ERROR_MSG,TITLE,0x10);ExitProcess(3);}
 base[end]=0;
 copystr(script,base,2048);append(script,RUN,2048);
 if(!exists(script)){MessageBoxW((PTR)0,ERROR_MSG,TITLE,0x10);ExitProcess(4);}
 param[0]='"';param[1]=0;append(param,script,2052);append(param,(const WCHAR[]){'"',0},2052);
 UINTPTR result=(UINTPTR)ShellExecuteW((PTR)0,OPEN,WSCRIPT,param,base,1);
 if(result<=32){MessageBoxW((PTR)0,ERROR_MSG,TITLE,0x10);ExitProcess(5);}
 ExitProcess(0);
}
